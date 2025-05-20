(ns sumisonic.figma-style-exporter.transform
  (:require [clojure.string :as str]
            [sumisonic.figma-style-exporter.constants :as const]))

(defn convert-css-props
  "Converts Figma style map to CSS-compatible format.
   Calculates lineHeight from lineHeightPx and fontSize in %.1f%% format.
   Args:
     style-props - Figma style properties map
   Returns:
     Map of CSS properties, or nil if conversion is not possible"
  [style-props]
  (let [px (get style-props "lineHeightPx")
        fs (get style-props "fontSize")
        lh (when (and px fs (not= fs 0))
             (format "%.1f%%" (* (/ px fs) 100)))]
    (when (and fs
               (contains? style-props "fontWeight")
               (or (get style-props "fontPostScriptName")
                   (get style-props "fontFamily"))
               (contains? style-props "letterSpacing")
               lh)
      {:fontSize fs,
       :fontWeight (get style-props "fontWeight"),
       :fontFamily (or (get style-props "fontPostScriptName")
                       (get style-props "fontFamily")),
       :letterSpacing (get style-props "letterSpacing"),
       :lineHeight lh})))

(defn map->css-props
  "Batch converts multiple Figma styles to CSS format.
   Filters out nil results from conversion.
   Args:
     flat-styles - Map of style names to Figma style properties
   Returns:
     Map of style names to CSS property maps"
  [flat-styles]
  (into {}
        (keep (fn [[style-name props]]
                (when-let [css-props (convert-css-props props)]
                  [style-name css-props]))
              flat-styles)))

(defn extract-parts
  "Extracts breakpoint, language, and name parts from a style name.
   Position of parts in the style name doesn't matter (order-independent).
   When require-breakpoint is true, breakpoints are extracted from the style name.
   When require-language is true, languages are extracted from the style name.
   Args:
     style-name - Style name string to parse
     breakpoints - Set of valid breakpoints
     languages - Set of valid languages
     require-breakpoint - Boolean flag indicating if breakpoint should be extracted
     require-language - Boolean flag indicating if language should be extracted
   Returns:
     Map with :base-name, optional :breakpoint, and optional :language keys"
  [style-name breakpoints languages require-breakpoint require-language]
  (let [parts (str/split style-name #"/")
        classified
        (map (fn [part]
               (cond
                 (and require-breakpoint (breakpoints part))
                 {:type :breakpoint, :value part}

                 (and require-language (languages part))
                 {:type :language, :value part}

                 :else
                 {:type :base-name, :value part}))
             parts)

        breakpoint (some #(when (= (:type %) :breakpoint) (:value %)) classified)
        language (some #(when (= (:type %) :language) (:value %)) classified)
        base-names (map :value (filter #(= (:type %) :base-name) classified))
        base-name (when (seq base-names)
                    (str/join "/" base-names))]

    (cond-> {:base-name (or base-name "")}
      breakpoint (assoc :breakpoint breakpoint)
      language (assoc :language language))))

(defn structure-style-output
  "Converts flat CSS style map to structured format based on style hierarchy.
   Args:
     css-styles - Map of CSS formatted styles
     breakpoints - Set of valid breakpoints
     languages - Set of valid languages
     require-breakpoint - Boolean flag indicating if breakpoint structure is required
     require-language - Boolean flag indicating if language structure is required
   Returns:
     Structured style map with appropriate hierarchy based on requirements"
  [css-styles breakpoints languages require-breakpoint require-language]
  (let [desired-order [:base-name :language :prop-key :breakpoint]]
    (reduce
     (fn [acc [style-name props]]
       (if-not (map? props)
         acc
         (let [parts (extract-parts style-name breakpoints languages require-breakpoint require-language)
               {:keys [base-name language breakpoint]} parts]
           (if (and base-name
                    (or (not require-breakpoint) breakpoint)
                    (or (not require-language) language))
             (reduce-kv
              (fn [acc' k v]
                (let [path-elements {:base-name base-name,
                                     :language language,
                                     :prop-key k,
                                     :breakpoint (when (and require-breakpoint breakpoint)
                                                   (keyword breakpoint))}
                      path (vec (remove nil? (map #(path-elements %) desired-order)))]
                  (assoc-in acc' path v)))
              acc
              props)
             acc))))
     {}
     css-styles)))

(defn extract-solid-color
  "Extracts the first solid color from Figma 'fills' list.
   Returns a map with keys :r, :g, :b, :a or nil if not found."
  [fills]
  (some (fn [fill]
          (when (and (= "SOLID" (get fill "type"))
                     (map? (get fill "color")))
            (let [color (get fill "color")
                  opacity (get fill "opacity" 1.0)]
              {"r" (get color "r"),
               "g" (get color "g"),
               "b" (get color "b"),
               "a" opacity})))
        fills))

(defn color-styles->simple-map
  "Converts raw color styles map into simplified color format.
   Input:
     {style-name → {:fills [...]}}
   Output:
     {style-name → {:color {:r ... :g ... :b ... :a ...}}}"
  [color-style-map]
  (into {}
        (keep (fn [[name {:keys [fills]}]]
                (when-let [color (extract-solid-color fills)]
                  [name {:color color}])))
        color-style-map))
