(ns sumisonic.figma-style-exporter.core
  (:require [sumisonic.figma-style-exporter.styles :as styles]
            [sumisonic.figma-style-exporter.transform :as transform]
            [sumisonic.figma-style-exporter.validate :as validate]
            [sumisonic.figma-style-exporter.constants :as constants]
            [sumisonic.figma-style-exporter.version :as version]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [cheshire.core :as json])
  (:gen-class))

(defn >>=
  "Monadic bind function. Executes next function if result is successful, propagates error otherwise.
   Args:
     result - Result map with :ok or :error key
     f - Function to apply to the :ok value
   Returns:
     Result of applying f to :ok value, or the original result if it contains an :error"
  [result f]
  (if-let [value (:ok result)]
    (f value)
    result))

(defn handle-error
  "Handles error information, displays details, and exits the program if an error is present.
   Args:
     result - Result map that may contain an :error
   Returns:
     Original result map unchanged"
  [result]
  (when-let [error (:error result)]
    (println "Error occurred:")
    (if (map? error)
      (do
        (when-let [type (:type error)]
          (println "  Type:" type))
        (when-let [msg (:message error)]
          (println "  Message:" msg))
        (when-let [details (:details error)]
          (println "  Details:" (pr-str details))))
      (println "  " error))
    (System/exit 1))
  result)

;; CLI argument definitions
(def cli-options
  [;; Use flags instead of options with values for enable/disable functionality
   ["-B" "--require-breakpoint" "Require breakpoint in style names"]
   ["-L" "--require-language" "Require language in style names"]

   ;; Specific values for breakpoints and languages
   ["-b" "--breakpoints BP1,BP2,..." "Comma-separated list of breakpoints (e.g., 'sm,md,lg,xl')"
    :parse-fn
    #(when (and % (not= % ""))
       (set (str/split % #",")))
    :default constants/breakpoints]
   ["-l" "--languages LANG1,LANG2,..." "Comma-separated list of languages (e.g., 'en,ja,zh')"
    :parse-fn
    #(when (and % (not= % ""))
       (set (str/split % #",")))
    :default constants/langs]

   ["-o" "--output OUTPUT" "Output file path (default: stdout)"
    :default nil]
   ["-h" "--help" "Display help"]])

(defn print-help
  "Displays help information.
   Args:
     summary - CLI options summary
   Returns:
     nil"
  [summary]
  (println "Usage: figma-style-exporter [options]")
  (println "Environment variables:")
  (println "  FIGMA_TOKEN      - Figma API token (required)")
  (println "  FIGMA_FILE_KEY   - Figma file key/ID (required)")
  (println "Options:")
  (println "  -B, --require-breakpoint     Require breakpoint in style names")
  (println "  -L, --require-language       Require language in style names")
  (println "  -b, --breakpoints BP1,BP2,...  Comma-separated list of breakpoints (default: sm,md,lg,xl)")
  (println "  -l, --languages LANG1,LANG2,...  Comma-separated list of languages (default: en,ja,zh,...)")
  (println "  -o, --output PATH            Output file path (if not specified, outputs to stdout)")
  (println "  -h, --help                   Display this help message"))

(defn write-output
  "Writes the JSON output to a file or stdout.
   Args:
     json-str - JSON string to output
     style-count - Number of styles processed
     output-path - Path to output file (nil for stdout)
   Returns:
     true on success, exits program on error"
  [json-str style-count output-path]
  (if output-path
    (try
      (spit output-path json-str)
      (println (str "Successfully processed " style-count
                    " style"
                    (when (not= style-count 1) "s")
                    " and saved to: " output-path))
      true
      (catch Exception e
        (println (str "Error writing to file: " (.getMessage e)))
        (System/exit 1)))

    ;; Output to stdout if no file specified
    (do
      (println json-str)
      (println (str "\nSuccessfully processed "
                    style-count
                    " style"
                    (when (not= style-count 1) "s")
                    "."))
      true)))

(defn process-styles
  "Processes Figma styles with validation and transformation.
   Args:
     token - Figma API token
     filekey - Figma file ID
     breakpoints - Set of valid breakpoints
     languages - Set of valid languages
     require-breakpoint - Boolean flag indicating if breakpoint is required in style names
     require-language - Boolean flag indicating if language is required in style names
   Returns:
     {:ok result-map} on success, or {:error details} on failure"
  [token filekey breakpoints languages require-breakpoint require-language]
  (-> {:ok [token filekey breakpoints languages require-breakpoint require-language]}
      ;; 1. Fetch style information
      (>>= (fn [[token filekey _ _ _ _]]
             {:ok (assoc (styles/collect-text-styles token filekey)
                         :breakpoints breakpoints
                         :languages languages)}))

      ;; 2. Convert to CSS format
      (>>= (fn [{:keys [ok breakpoints languages]}]
             (let [flat-css-styles (transform/map->css-props ok)]
               {:ok {:flat-css-styles flat-css-styles,
                     :breakpoints breakpoints,
                     :languages languages}})))

      ;; 3. CSS validation
      (>>= (fn [{:keys [flat-css-styles breakpoints languages]}]
             (let [css-validation (validate/validate-css-style-map flat-css-styles)]
               (if (:ok css-validation)
                 {:ok {:flat-css-styles flat-css-styles,
                       :breakpoints breakpoints,
                       :languages languages}}
                 css-validation))))

      ;; 4. Style name structure validation (pass breakpoints and requirement flags)
      (>>= (fn [{:keys [flat-css-styles breakpoints languages]}]
             (let [names-validation (validate/validate-style-names
                                     flat-css-styles
                                     breakpoints
                                     languages
                                     require-breakpoint
                                     require-language)]
               (if-let [style-names (:ok names-validation)]
                 {:ok {:flat-css-styles flat-css-styles,
                       :style-names style-names,
                       :breakpoints breakpoints,
                       :languages languages}}
                 names-validation))))

      ;; 5. Style name pattern consistency validation
      (>>=
       (fn [{:keys [flat-css-styles style-names breakpoints languages]}]
         (let [patterns-validation
               (validate/validate-style-patterns style-names breakpoints languages require-breakpoint require-language)]
           (if (:ok patterns-validation)
             {:ok {:flat-css-styles flat-css-styles,
                   :breakpoints breakpoints,
                   :languages languages}}
             patterns-validation))))

      ;; 6. Convert to structured format
      (>>= (fn [{:keys [flat-css-styles breakpoints languages]}]
             (let [structured-styles
                   (transform/structure-style-output flat-css-styles
                                                     breakpoints
                                                     languages
                                                     require-breakpoint
                                                     require-language)]
               {:ok {:structured-styles structured-styles,
                     :style-count (count flat-css-styles)}})))

      ;; Error handling
      handle-error))

(defn process-colors
  "Processes FILL (color) styles from a Figma file.
   Args:
     token - Figma API token
     filekey - Figma file ID
   Returns:
     {:ok {:color-styles ... :style-count N}} or {:error ...}"
  [token filekey]
  (-> {:ok [token filekey]}
      ;; Fetch raw color styles
      (>>= (fn [[token filekey]]
             (styles/collect-color-styles token filekey)))
      ;; Transform to simplified format
      (>>= (fn [color-styles]
             (let [simple (transform/color-styles->simple-map color-styles)]
               {:ok {:color-styles simple,
                     :style-count (count simple)}})))
      ;; Handle errors
      handle-error))

(defn -main
  "Main entry point for the Figma style exporter with subcommand support.
   Usage: figma-style-exporter <command> [options]

   Commands:
     text  - Export text styles
     color - Export color styles"
  [& args]
  (when (some #{"--version" "-v"} args)
    (println "figma-style-exporter version" version/version)
    (System/exit 0))
  (let [[command & sub-args] args]
    (case command
      "text"
      (let [parsed-opts (parse-opts sub-args cli-options)
            options (:options parsed-opts)
            errors (:errors parsed-opts)
            output-path (:output options)]

        ;; Display errors if any occurred during parsing
        (when errors
          (doseq [error errors]
            (println error))
          (System/exit 1))

        (when (:help options)
          (print-help (:summary parsed-opts))
          (System/exit 0))

        (let [token (System/getenv "FIGMA_TOKEN")
              filekey (or (System/getenv "FIGMA_FILE_KEY")
                          (System/getenv "FIGMA_FILE_ID"))

              ;; Get require flags and values directly from parsed options
              require-breakpoint (:require-breakpoint options)
              require-language (:require-language options)
              breakpoints (:breakpoints options)
              languages (:languages options)

              ;; Debug output
              _ (println "Requirements:"
                         (when require-breakpoint "breakpoint")
                         (when require-language "language"))
              _ (println "Using breakpoints:" breakpoints)
              _ (println "Using languages:" languages)]

          (if-not (and token filekey)
            (do
              (println "Required: FIGMA_TOKEN and FIGMA_FILE_ID/FILE_KEY")
              (System/exit 1))

            (when-let [{:keys [structured-styles style-count]}
                       (:ok (process-styles token
                                            filekey
                                            breakpoints
                                            languages
                                            require-breakpoint
                                            require-language))]
              (let [json-str (json/generate-string structured-styles {:pretty true})]
                (write-output json-str style-count output-path))))))

      "color"
      (let [parsed-opts (parse-opts sub-args
                                    [["-o" "--output OUTPUT" "Output file path"]
                                     ["-h" "--help"]])
            options (:options parsed-opts)
            output-path (:output options)]

        (when (:help options)
          (println "Usage: figma-style-exporter color [options]")
          (println "Options:")
          (println "  -o, --output PATH     Output file path (default: stdout)")
          (println "  -h, --help            Display this help message")
          (System/exit 0))

        (let [token (System/getenv "FIGMA_TOKEN")
              filekey (or (System/getenv "FIGMA_FILE_KEY")
                          (System/getenv "FIGMA_FILE_ID"))]

          (if-not (and token filekey)
            (do
              (println "Required: FIGMA_TOKEN and FIGMA_FILE_ID/FILE_KEY")
              (System/exit 1))

            (when-let [{:keys [color-styles style-count]}
                       (:ok (process-colors token filekey))]
              (let [json-str (json/generate-string color-styles {:pretty true})]
                (write-output json-str style-count output-path))))))

      ;; fallback
      (do
        (println "Unknown or missing command.")
        (println "Usage: figma-style-exporter <command> [options]")
        (println "Commands: text | color")
        (System/exit 1)))))
