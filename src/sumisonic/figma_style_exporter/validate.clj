(ns sumisonic.figma-style-exporter.validate
  (:require [clojure.string :as str]
            [sumisonic.figma-style-exporter.transform :as transform]
            [sumisonic.figma-style-exporter.constants :as const]
            [malli.core :as m]
            [malli.error :as me]))

(def CssStyle
  "Schema for validating individual CSS style objects."
  [:map
   [:fontSize double?]
   [:fontWeight int?]
   [:fontFamily string?]
   [:letterSpacing double?]
   [:lineHeight [:re #".+%"]]])

(def CssStyleMap
  "Schema for validating the complete CSS style map."
  [:map-of string? CssStyle])

(defn validate-css-style-map
  "Validates a CSS style map using Malli schema.
   Args:
     style-map - Map of style names to CSS property maps
   Returns:
     {:ok true} on success, or
     {:error {:type :css-validation, :details error-details}} on failure"
  [style-map]
  (if (m/validate CssStyleMap style-map)
    {:ok true}
    {:error {:type :css-validation,
             :details (me/humanize (m/explain CssStyleMap style-map))}}))

(defn validate-style-patterns
  "Validates consistency of style name patterns.
   Checks if all style names follow the same pattern structure.
   Args:
     style-names - List of style names to validate
     breakpoints - Set of valid breakpoints
     languages - Set of valid languages
     require-breakpoint - Boolean flag indicating if breakpoint is required
     require-language - Boolean flag indicating if language is required
   Returns:
     {:ok {:pattern detected-pattern}} on success, or
     {:error error-details} on failure"
  [style-names breakpoints languages require-breakpoint require-language]
  (let [classify (fn [part]
                   (cond
                     (and require-breakpoint (breakpoints part)) :breakpoint
                     (and require-language (languages part)) :language
                     :else :base-name))
        patterns (map (fn [name]
                        (let [parts (str/split name #"/")]
                          (mapv classify parts)))
                      style-names)
        unique-patterns (distinct patterns)]

    (cond
      ;; More than one pattern (inconsistent)
      (> (count unique-patterns) 1)
      {:error {:type :inconsistent-patterns,
               :details {:patterns unique-patterns,
                         :pattern_mismatches (map vector style-names patterns)}}}

      ;; Passes all validations
      :else
      {:ok {:pattern (first unique-patterns)}})))

(defn validate-style-names
  "Validates style names according to requirements.
   When require-breakpoint is true, all style names must have a breakpoint.
   When require-language is true, all style names must have a language.
   Args:
     css-styles - Map of CSS formatted styles
     breakpoints - Set of valid breakpoints
     languages - Set of valid languages
     require-breakpoint - Boolean flag indicating if breakpoint is required
     require-language - Boolean flag indicating if language is required
   Returns:
     {:ok style-names} list of validated style names on success, or
     {:error error-details} on failure"
  [css-styles breakpoints languages require-breakpoint require-language]
  (let [style-names (keys css-styles)
        ;; Check style names against requirements
        missing-parts
        (filter (fn [name]
                  (let [parts (transform/extract-parts
                               name
                               breakpoints
                               languages
                               require-breakpoint
                               require-language)
                        has-breakpoint (:breakpoint parts)
                        has-language (:language parts)]
                    (or
                     (and require-breakpoint (not has-breakpoint))
                     (and require-language (not has-language)))))
                style-names)]

    (if (seq missing-parts)
      ;; Some style names missing required parts
      {:error {:type :invalid-style-names,
               :details {:invalid_style_names missing-parts,
                         :count (count missing-parts),
                         :require_breakpoint require-breakpoint,
                         :require_language require-language}}}

      ;; All style names have required parts
      {:ok style-names})))
