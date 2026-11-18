(ns sumisonic.figma-style-exporter.styles-test
  (:require [clojure.test :refer :all]
            [sumisonic.figma-style-exporter.styles :as styles]))

(defn- entry
  [id style-name]
  [id {"key" (str "key-" id), "name" style-name, "styleType" "TEXT", "remote" false}])

(defn- names
  [result]
  (map #(get-in % [1 "name"]) (:ok result)))

(deftest filter-by-prefix-test
  (let [entries [(entry "1" "2026/sm/Body")
                 (entry "2" "2026/md/Body")
                 (entry "3" "2025/sm/Body")
                 (entry "4" "sm/Body")]]

    (testing "keeps every entry unchanged without a prefix"
      (is (= {:ok entries} (styles/filter-by-prefix entries nil))))

    (testing "keeps only the names that start with the prefix, and removes the prefix"
      (let [result (styles/filter-by-prefix entries "2026/")]
        (is (= ["1" "2"] (map first (:ok result))))
        (is (= ["sm/Body" "md/Body"] (names result)))))

    (testing "keeps the other fields of an entry"
      (is (= {"key" "key-1", "name" "sm/Body", "styleType" "TEXT", "remote" false}
             (second (first (:ok (styles/filter-by-prefix entries "2026/"))))))))

  (testing "matches only at the start of the name"
    (is (= ["sm/Body"]
           (names (styles/filter-by-prefix [(entry "1" "2026/sm/Body")
                                            (entry "2" "x2026/sm/Body")]
                                           "2026/")))))

  (testing "matches literally, not as a regular expression"
    (is (= ["/Body"]
           (names (styles/filter-by-prefix [(entry "1" "2026./Body")
                                            (entry "2" "2026a/Body")]
                                           "2026."))))
    (is (= :no-styles-match-prefix
           (get-in (styles/filter-by-prefix [(entry "1" "2026/Body")] ".*") [:error :type]))))

  (testing "leaves the separator when the prefix does not include it"
    (is (= ["/sm/Body"]
           (names (styles/filter-by-prefix [(entry "1" "2026/sm/Body")] "2026")))))

  (testing "turns a name equal to the prefix into an empty name"
    (is (= [""]
           (names (styles/filter-by-prefix [(entry "1" "2026/")] "2026/")))))

  (testing "skips entries without a name"
    (is (= ["Body"]
           (names (styles/filter-by-prefix [["1" {"styleType" "TEXT"}]
                                            (entry "2" "2026/Body")]
                                           "2026/")))))

  (testing "returns an error when no name starts with the prefix"
    (let [result (styles/filter-by-prefix [(entry "1" "2026/sm/Body")] "2027/")]
      (is (= :no-styles-match-prefix (get-in result [:error :type])))
      (is (= {:prefix "2027/", :checked-style-count 1} (get-in result [:error :details])))
      (is (re-find #"2027/" (get-in result [:error :message])))))

  (testing "returns an error for an empty sequence with a prefix, and the empty sequence without one"
    (is (= :no-styles-match-prefix
           (get-in (styles/filter-by-prefix [] "2026/") [:error :type])))
    (is (= {:ok []} (styles/filter-by-prefix [] nil)))))
