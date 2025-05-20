(ns sumisonic.figma-style-exporter.constants
  "Defines constants used throughout the Figma style exporter.
   Contains sets of supported breakpoints and language codes.")

;; Constants definition
(def breakpoints
  "Set of supported CSS breakpoint identifiers."
  #{"sm" "md" "lg" "xl"})

(def langs
  "Set of supported language codes.
   Includes 'all' as an internal marker and BCP 47 compliant language tags."
  #{
    ;; Internal shared style marker (not valid for HTML lang="")
    "all"
    ;; BCP 47 compliant language tags (valid for <html lang="...">)
    "ja"      ; Japanese
    "en"      ; English
    "zh"      ; Chinese (unspecified)
    "zh-Hans" ; Simplified Chinese (Mainland China)
    "zh-Hant" ; Traditional Chinese (Taiwan, Hong Kong)
    "ko"      ; Korean
    "fr"      ; French
    "de"      ; German
    "es"      ; Spanish
    "it"      ; Italian
    "pt"      ; Portuguese
    "ru"      ; Russian
    "ar"      ; Arabic
    "hi"      ; Hindi
   })
