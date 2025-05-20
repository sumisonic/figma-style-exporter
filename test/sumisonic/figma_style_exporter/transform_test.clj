(ns sumisonic.figma-style-exporter.transform-test
  (:require [clojure.test :refer :all]
            [sumisonic.figma-style-exporter.transform :as transform]
            [sumisonic.figma-style-exporter.constants :as const]))

(deftest test-convert-css-props
  (testing "returns a CSS map when all required keys are present"
    (let [style-props {"lineHeightPx" 24.0,
                       "fontSize" 12.0,
                       "fontWeight" 400,
                       "fontFamily" "Inter",
                       "letterSpacing" 1.0}
          result (transform/convert-css-props style-props)]
      (is (= 12.0 (:fontSize result)))
      (is (= 400 (:fontWeight result)))
      (is (= "Inter" (:fontFamily result)))
      (is (= 1.0 (:letterSpacing result)))
      (is (= "200.0%" (:lineHeight result)))))

  (testing "returns nil when required keys are missing"
    (is (nil? (transform/convert-css-props {"fontSize" 12.0})))))

(deftest test-map->css-props
  (testing "filters out entries that convert to nil"
    (let [input {"foo" {"lineHeightPx" 24.0,
                        "fontSize" 12.0,
                        "fontWeight" 400,
                        "fontFamily" "Inter",
                        "letterSpacing" 1.0},
                 "bar" {"fontSize" 12.0}}
          result (transform/map->css-props input)]
      (is (contains? result "foo"))
      (is (not (contains? result "bar"))))))

(deftest test-extract-parts
  (testing "parses parts regardless of order when both breakpoint and language required"
    (let [{:keys [base-name breakpoint language]}
          (transform/extract-parts "foo/en/sm" const/breakpoints const/langs true true)]
      (is (= "foo" base-name))
      (is (= "sm" breakpoint))
      (is (= "en" language))))

  (testing "parses when order is different and both required"
    (let [{:keys [base-name breakpoint language]}
          (transform/extract-parts "sm/foo/en" const/breakpoints const/langs true true)]
      (is (= "foo" base-name))
      (is (= "sm" breakpoint))
      (is (= "en" language))))

  (testing "treats breakpoint as part of base-name when not required"
    (let [{:keys [base-name breakpoint language]}
          (transform/extract-parts "foo/sm/en" const/breakpoints const/langs false true)]
      (is (= "foo/sm" base-name))
      (is (nil? breakpoint))
      (is (= "en" language))))

  (testing "treats language as part of base-name when not required"
    (let [{:keys [base-name breakpoint language]}
          (transform/extract-parts "foo/en/sm" const/breakpoints const/langs true false)]
      (is (= "foo/en" base-name))
      (is (= "sm" breakpoint))
      (is (nil? language))))

  (testing "treats both breakpoint and language as part of base-name when neither required"
    (let [{:keys [base-name breakpoint language]}
          (transform/extract-parts "foo/en/sm" const/breakpoints const/langs false false)]
      (is (= "foo/en/sm" base-name))
      (is (nil? breakpoint))
      (is (nil? language)))))

(deftest test-structure-style-output
  (testing "structures styles by name and breakpoint when breakpoint required"
    (let [css-styles {"foo/sm" {:a 1}}
          result (transform/structure-style-output css-styles const/breakpoints const/langs true false)]
      (is (= {"foo" {:a {:sm 1}}} result))))

  (testing "includes language level in nested map when language required"
    (let [css-styles {"bar/en/md" {:b 2}}
          result (transform/structure-style-output css-styles const/breakpoints const/langs true true)]
      (is (= {"bar" {"en" {:b {:md 2}}}} result))))

  (testing "keeps breakpoint as part of name when not required"
    (let [css-styles {"foo/sm" {:a 1}}
          result (transform/structure-style-output css-styles const/breakpoints const/langs false false)]
      (is (= {"foo/sm" {:a 1}} result))))

  (testing "keeps language as part of name when not required"
    (let [css-styles {"bar/en/md" {:b 2}}
          result (transform/structure-style-output css-styles const/breakpoints const/langs true false)]
      (is (= {"bar/en" {:b {:md 2}}} result)))))

(deftest test-extract-solid-color
  (testing "extracts the first solid fill's color"
    (let [fills [{"type" "GRADIENT"}
                 {"type" "SOLID", "color" {"r" 0.1, "g" 0.2, "b" 0.3}, "opacity" 0.5}]
          color (transform/extract-solid-color fills)]
      (is (= {"r" 0.1, "g" 0.2, "b" 0.3, "a" 0.5} color))))
  (testing "returns nil if no solid fill is present"
    (is (nil? (transform/extract-solid-color [{"type" "IMAGE"}])))))

(deftest test-color-styles->simple-map
  (testing "converts SOLID fills to simple color map"
    (let [color-map {"foo" {:fills [{"type" "SOLID", "color" {"r" 0.4, "g" 0.5, "b" 0.6}}]},
                     "bar" {:fills [{"type" "GRADIENT"}]}}
          result (transform/color-styles->simple-map color-map)]
      (is (= {"foo" {:color {"r" 0.4, "g" 0.5, "b" 0.6, "a" 1.0}}} result))
      (is (not (contains? result "bar"))))))
