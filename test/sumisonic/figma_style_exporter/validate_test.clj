(ns sumisonic.figma-style-exporter.validate-test
  (:require [clojure.test :refer :all]
            [sumisonic.figma-style-exporter.validate :as validate]
            [sumisonic.figma-style-exporter.constants :as const]))

(deftest test-validate-css-style-map
  (testing "valid CSS styles"
    (let [valid-styles
          {"sm/foo/en" {:fontSize 16.0,
                        :fontWeight 400,
                        :fontFamily "Inter",
                        :letterSpacing 0.0,
                        :lineHeight "120.0%"}}]
      (is (= {:ok true}
             (validate/validate-css-style-map valid-styles)))))

  (testing "invalid CSS styles"
    (let [invalid-styles
          {"sm/bar" {:fontSize "large",
                     :fontWeight "bold",
                     :fontFamily nil,
                     :letterSpacing 0.0,
                     :lineHeight "100"}}] ; lineHeight missing `%`
      (is (= :css-validation
             (:type (:error (validate/validate-css-style-map invalid-styles))))))))

(deftest test-validate-style-names
  (testing "valid style names with breakpoint required"
    (let [styles {"md/en/hoge" {:fontSize 12.0}}]
      (is (= {:ok ["md/en/hoge"]}
             (validate/validate-style-names styles const/breakpoints const/langs true false)))))

  (testing "valid style names with language required"
    (let [styles {"md/en/hoge" {:fontSize 12.0}}]
      (is (= {:ok ["md/en/hoge"]}
             (validate/validate-style-names styles const/breakpoints const/langs false true)))))

  (testing "valid style names with both breakpoint and language required"
    (let [styles {"md/en/hoge" {:fontSize 12.0}}]
      (is (= {:ok ["md/en/hoge"]}
             (validate/validate-style-names styles const/breakpoints const/langs true true)))))

  (testing "valid style names with neither breakpoint nor language required"
    (let [styles {"md/en/hoge" {:fontSize 12.0}}]
      (is (= {:ok ["md/en/hoge"]}
             (validate/validate-style-names styles const/breakpoints const/langs false false)))))

  (testing "missing breakpoint when required"
    (let [styles {"en/hoge" {:fontSize 12.0}}]
      (is (= :invalid-style-names
             (:type (:error (validate/validate-style-names styles const/breakpoints const/langs true false)))))))

  (testing "missing language when required"
    (let [styles {"md/hoge" {:fontSize 12.0}}]
      (is (= :invalid-style-names
             (:type (:error (validate/validate-style-names styles const/breakpoints const/langs false true)))))))

  (testing "missing both breakpoint and language when both required"
    (let [styles {"hoge" {:fontSize 12.0}}]
      (is (= :invalid-style-names
             (:type (:error (validate/validate-style-names styles const/breakpoints const/langs true true))))))))

(deftest test-validate-style-patterns
  (testing "consistent pattern"
    (let [names ["md/ja/foo" "md/ja/bar"]]
      (is (= :ok
             (first (keys (validate/validate-style-patterns names const/breakpoints const/langs true true)))))))

  (testing "inconsistent pattern"
    (let [names ["md/ja/foo" "ja/bar/md"]]
      (is (= :inconsistent-patterns
             (:type (:error (validate/validate-style-patterns names const/breakpoints const/langs true true)))))))

  (testing "pattern check succeeds even without breakpoint or language"
    (let [names ["foo/bar" "baz/qux"]]
      (is (= :ok
             (first (keys (validate/validate-style-patterns names const/breakpoints const/langs true true))))))))
