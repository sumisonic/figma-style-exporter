(ns sumisonic.figma-style-exporter.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [cheshire.core :as json]
            [sumisonic.figma-style-exporter.core :as core]
            [sumisonic.figma-style-exporter.constants :as const]
            [sumisonic.figma-style-exporter.fake-figma :as fake]))

;; These tests run the text and color exports end to end, with the Figma API
;; replaced by fake-figma. handle-error exits the JVM on an error, so it is
;; replaced by identity here, and the error result is checked instead.

(def sm-md #{"sm" "md"})

(defn- export-text
  "Runs the text export over file with -B -b sm,md and the given prefix."
  [file prefix]
  (with-redefs [core/handle-error identity]
    (fake/call-with-file file
                         #(core/process-styles "token" "file" sm-md const/langs true false prefix))))

(defn- export-colors
  [file prefix]
  (with-redefs [core/handle-error identity]
    (fake/call-with-file file #(core/process-colors "token" "file" prefix))))

(defn- text-props-by-breakpoint
  "The structured output of one text style name, as the exporter writes it with -B."
  [sizes]
  {:fontSize sizes,
   :fontWeight (update-vals sizes (constantly 400)),
   :fontFamily (update-vals sizes (constantly "Inter-Regular")),
   :letterSpacing (update-vals sizes (constantly 0.0)),
   :lineHeight (update-vals sizes (constantly "150.0%"))})

(deftest text-prefix-test
  (testing "keeps the styles under the prefix and removes the prefix from the output keys"
    (let [{:keys [result]} (export-text fake/two-years-file "2026/")]
      (is (= {:ok {:structured-styles {"Body" (text-props-by-breakpoint {:sm 14.0, :md 16.0})},
                   :style-count 2}}
             result))))

  (testing "passes the name pattern check with a prefix, though the file mixes two name shapes"
    (is (= :inconsistent-patterns
           (get-in (:result (export-text fake/two-years-file nil)) [:error :type])))
    (is (contains? (:result (export-text fake/two-years-file "2026/")) :ok)))

  (testing "selects the older styles with their own prefix"
    (is (= {"Body" (text-props-by-breakpoint {:sm 13.0})}
           (get-in (:result (export-text fake/two-years-file "2025/")) [:ok :structured-styles]))))

  (testing "asks Figma only for the styles under the prefix"
    (let [{:keys [node-ids style-keys]} (export-text fake/two-years-file "2026/")]
      (is (= #{"1:1" "1:2"} (set node-ids)))
      (is (= ["key-9:1"] style-keys))))

  (testing "returns an error and asks Figma for nothing when no style matches"
    (let [{:keys [result node-ids style-keys]} (export-text fake/two-years-file "2027/")]
      (is (= :no-styles-match-prefix (get-in result [:error :type])))
      (is (empty? node-ids))
      (is (empty? style-keys)))))

(deftest color-prefix-test
  (testing "keeps the colors under the prefix and removes the prefix from the output keys"
    (let [{:keys [result node-ids]} (export-colors fake/two-years-file "2026/")]
      (is (= {:ok {:color-styles {"Primary" {:color {"r" 0.1, "g" 0.2, "b" 0.3, "a" 1.0}}},
                   :style-count 1}}
             result))
      (is (= ["2:1"] node-ids))))

  (testing "returns an error and asks Figma for nothing when no color matches"
    (let [{:keys [result node-ids]} (export-colors fake/two-years-file "2027/")]
      (is (= :no-styles-match-prefix (get-in result [:error :type])))
      (is (empty? node-ids)))))

;; The expected values below were recorded by running the same fake files
;; through the code before --prefix was added (version 1.0.1).
(deftest no-prefix-keeps-the-previous-output-test
  (testing "text with -B -b sm,md"
    (let [{:keys [result node-ids style-keys]} (export-text fake/single-shape-file nil)]
      (is (= {:ok {:structured-styles
                   {"Body" (text-props-by-breakpoint {:sm 14.0, :md 16.0}),
                    "Heading" (text-props-by-breakpoint {:sm 24.0, :md 32.0})},
                   :style-count 4}}
             result))
      (is (= #{"1:1" "1:2" "1:3" "1:4"} (set node-ids)))
      (is (= ["key-9:1"] style-keys))))

  (testing "text without -B or -L"
    (let [props (fn [size]
                  {:fontSize size, :fontWeight 400, :fontFamily "Inter-Regular",
                   :letterSpacing 0.0, :lineHeight "150.0%"})]
      (is (= {:ok {:structured-styles {"sm/Body" (props 14.0),
                                       "md/Body" (props 16.0),
                                       "sm/Heading" (props 24.0),
                                       "md/Heading" (props 32.0)},
                   :style-count 4}}
             (with-redefs [core/handle-error identity]
               (:result (fake/call-with-file
                         fake/single-shape-file
                         #(core/process-styles "token" "file"
                                               const/breakpoints const/langs nil nil nil))))))))

  (testing "color"
    (let [{:keys [result node-ids]} (export-colors fake/two-years-file nil)]
      (is (= {:ok {:color-styles {"Primary" {:color {"r" 0.7, "g" 0.8, "b" 0.9, "a" 1.0}},
                                  "2026/Primary" {:color {"r" 0.1, "g" 0.2, "b" 0.3, "a" 1.0}},
                                  "2025/Primary" {:color {"r" 0.4, "g" 0.5, "b" 0.6, "a" 1.0}}},
                   :style-count 3}}
             result))
      (is (= #{"2:1" "2:2" "2:3"} (set node-ids))))))

(deftest api-error-test
  ;; Before --prefix, the text export turned an API error into {:ok nil} and lost it.
  (testing "the text export returns the API error, with or without a prefix"
    (doseq [prefix [nil "2026/"]]
      (is (= :api-error
             (get-in (with-redefs [core/handle-error identity]
                       (fake/call-with-api-error
                        #(core/process-styles "token" "file" sm-md const/langs true false prefix)))
                     [:error :type]))
          (str "prefix " (pr-str prefix)))))

  (testing "the color export returns the API error, with or without a prefix"
    (doseq [prefix [nil "2026/"]]
      (is (= :api-error
             (get-in (with-redefs [core/handle-error identity]
                       (fake/call-with-api-error #(core/process-colors "token" "file" prefix)))
                     [:error :type]))
          (str "prefix " (pr-str prefix))))))

(deftest prefix-option-test
  (doseq [[command spec] [["text" core/cli-options] ["color" core/color-cli-options]]]
    (testing (str command " reads the prefix")
      (is (= "2026/" (:prefix (:options (parse-opts ["--prefix" "2026/"] spec)))))
      (is (= "2026/" (:prefix (:options (parse-opts ["--prefix=2026/"] spec))))))

    (testing (str command " leaves the prefix unset when it is not given")
      (let [{:keys [options errors]} (parse-opts [] spec)]
        (is (nil? (:prefix options)))
        (is (nil? errors))))

    (testing (str command " rejects an empty prefix and a missing value")
      (is (seq (:errors (parse-opts ["--prefix" ""] spec))))
      (is (seq (:errors (parse-opts ["--prefix"] spec)))))

    (testing (str command " reports a mistyped option")
      (is (seq (:errors (parse-opts ["--prefx" "2026/"] spec)))))))

(deftest help-test
  (testing "the text help and the color help both describe --prefix"
    (is (str/includes? (with-out-str (core/print-help nil)) "--prefix PREFIX"))
    (is (str/includes? (with-out-str (core/print-color-help)) "--prefix PREFIX"))))

;; --- stdout carries only the JSON ------------------------------------------

(defn- capture-streams
  "Calls f and returns what it wrote to stdout and to stderr."
  [f]
  (let [out (java.io.StringWriter.)
        err (java.io.StringWriter.)
        value (binding [*out* out, *err* err] (f))]
    {:value value, :out (str out), :err (str err)}))

(deftest stdout-is-json-only-test
  (let [styles {"Body" {:fontSize {:sm 14.0}}}
        json-str (json/generate-string styles {:pretty true})]

    (testing "without -o, stdout is the JSON alone and the summary goes to stderr"
      (let [{:keys [value out err]} (capture-streams #(core/write-output json-str 1 nil))]
        (is (true? value))
        (is (= {"Body" {"fontSize" {"sm" 14.0}}} (json/parse-string out)))
        (is (= (str json-str "\n") out))
        (is (str/includes? err "Successfully processed 1 style."))))

    (testing "with -o, the JSON goes to the file and stdout stays empty"
      (let [file (java.io.File/createTempFile "figma-style-exporter-test" ".json")]
        (try
          (let [{:keys [out err]} (capture-streams #(core/write-output json-str 2 (str file)))]
            (is (= "" out))
            (is (str/includes? err "Successfully processed 2 styles and saved to: "))
            (is (= json-str (slurp file))))
          (finally
            (io/delete-file file true))))))

  (testing "errors go to stderr"
    (let [{:keys [out err]} (capture-streams
                             #(core/print-error {:type :no-styles-match-prefix,
                                                 :message "No styles start with the prefix \"2027/\"",
                                                 :details {:prefix "2027/"}}))]
      (is (= "" out))
      (is (str/includes? err "Error occurred:"))
      (is (str/includes? err "Type: :no-styles-match-prefix"))
      (is (str/includes? err "Message: No styles start with the prefix \"2027/\""))))

  (testing "the text settings go to stderr"
    (let [{:keys [out err]} (capture-streams
                             #(core/print-text-settings true false #{"sm"} #{"en"}))]
      (is (= "" out))
      (is (str/includes? err "Requirements: breakpoint"))
      (is (str/includes? err "Using breakpoints: #{sm}"))
      (is (str/includes? err "Using languages: #{en}")))))
