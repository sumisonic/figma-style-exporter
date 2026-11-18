(ns sumisonic.figma-style-exporter.styles
  (:require [clojure.string :as str]
            [sumisonic.figma-style-exporter.api :as api]))

(defn filter-by-prefix
  "Keeps the style entries whose name starts with prefix, and removes prefix from their names.
   The prefix is matched literally (not as a regular expression), so include the
   separator in it, e.g. \"2026/\".
   Args:
     style-entries - Sequence of [id style-data] entries
     prefix - Literal string the names must start with, or nil to keep every entry unchanged
   Returns:
     {:ok entries} with the prefix removed from each name, or
     {:error {:type :no-styles-match-prefix ...}} when a prefix is given and no name starts with it"
  [style-entries prefix]
  (if (nil? prefix)
    {:ok style-entries}
    (let [matched (for [[id data] style-entries
                        :let [style-name (get data "name")]
                        :when (and (string? style-name)
                                   (str/starts-with? style-name prefix))]
                    [id (assoc data "name" (subs style-name (count prefix)))])]
      (if (seq matched)
        {:ok matched}
        {:error {:type :no-styles-match-prefix,
                 :message (str "No styles start with the prefix " (pr-str prefix)
                               ". The prefix is matched literally, so include the separator"
                               " (e.g. \"2026/\").")
                 :details {:prefix prefix,
                           :checked-style-count (count style-entries)}}}))))

(defn text-style-metas
  "Extracts TEXT type style entries from style metadata.
   Args:
     styles-meta - Style metadata retrieved from Figma API
   Returns:
     Sequence of [id style-data] entries for TEXT type styles"
  [styles-meta]
  (->> styles-meta
       (filter #(= "TEXT" (get-in % [1 "styleType"])))))

(defn fetch-local-styles
  "Fetches locally defined TEXT styles by node_id in batch.
   Args:
     token - Figma API token string
     filekey - Figma file ID
     style-entries - Sequence of [id style-data] entries
   Returns:
     {:ok {style-name → style-map}} on success, or
     {:error {:type :local-styles-error :message \"...\" :data {...}}} on failure"
  [token filekey style-entries]
  (let [locals (remove #(get-in % [1 "remote"]) style-entries)
        ids (map first locals)
        nodes-result (api/fetch-nodes token filekey ids)]
    (if (:error nodes-result)
      nodes-result ;; Return error as is
      (let [nodes (:ok nodes-result)]
        ;; Map of name -> style information
        (try
          {:ok (into {}
                     (for [[id {:strs [name]}] locals
                           :let [style (get-in nodes [id "document" "style"])]
                           :when style]
                       [name style]))}
          (catch Exception e
            {:error {:type :local-styles-error,
                     :message (.getMessage e),
                     :data (ex-data e)}}))))))

(defn fetch-remote-styles
  "Fetches remotely defined TEXT styles by style key individually.
   Args:
     token - Figma API token string
     style-entries - Sequence of [id style-data] entries
   Returns:
     {:ok {style-name → style-map}} on success, or
     {:error {:type :remote-styles-error :message \"...\" :data {...}}} on failure"
  [token style-entries]
  (let [remotes (filter #(get-in % [1 "remote"]) style-entries)]
    (try
      {:ok (into {}
                 (for [[_ {:strs [key name]}] remotes
                       :let [style-result (api/fetch-style token key)]
                       :when (not (:error style-result))]
                   [name (:ok style-result)]))}
      (catch Exception e
        {:error {:type :remote-styles-error,
                 :message (.getMessage e),
                 :data (ex-data e)}}))))

(defn collect-text-styles
  "Collects the TEXT styles from a Figma file.
   With a prefix, only the styles whose name starts with it are fetched, and the
   prefix is removed from their names (see filter-by-prefix).
   Args:
     token - Figma API token string
     filekey - Figma file ID
     prefix - Literal name prefix, or nil for every TEXT style
   Returns:
     {:ok {style-name → style-map}} on success, or
     {:error {...}} on failure"
  ([token filekey]
   (collect-text-styles token filekey nil))
  ([token filekey prefix]
   (let [styles-meta-result (api/fetch-file-styles-meta token filekey)]
     (if (:error styles-meta-result)
       styles-meta-result
       (let [styles-meta (:ok styles-meta-result)
             filtered (filter-by-prefix (text-style-metas styles-meta) prefix)]
         (if (:error filtered)
           filtered
           (let [txt-entries (:ok filtered)
                 local-result (fetch-local-styles token filekey txt-entries)
                 remote-result (fetch-remote-styles token txt-entries)]
             (cond
               (:error local-result) local-result
               (:error remote-result) remote-result
               :else {:ok (merge (:ok local-result) (:ok remote-result))}))))))))

(defn color-style-metas
  "Extracts FILL type style entries from style metadata.
   Args:
     styles-meta - Style metadata retrieved from Figma API
   Returns:
     Sequence of [id style-data] entries for FILL type styles"
  [styles-meta]
  (->> styles-meta
       (filter #(= "FILL" (get-in % [1 "styleType"])))))

(defn collect-color-styles
  "Collects the FILL (color) styles from a Figma file.
   With a prefix, only the styles whose name starts with it are fetched, and the
   prefix is removed from their names (see filter-by-prefix).
   Args:
     token - Figma API token string
     filekey - Figma file ID
     prefix - Literal name prefix, or nil for every FILL style
   Returns:
     {:ok {style-name → {:fills [...]}} on success, or
     {:error {...}} on failure"
  ([token filekey]
   (collect-color-styles token filekey nil))
  ([token filekey prefix]
   (let [styles-meta-result (api/fetch-file-styles-meta token filekey)]
     (if (:error styles-meta-result)
       styles-meta-result
       (let [styles-meta (:ok styles-meta-result)
             filtered (filter-by-prefix (color-style-metas styles-meta) prefix)]
         (if (:error filtered)
           filtered
           (let [fill-entries (:ok filtered)
                 ids (map first fill-entries)
                 nodes-result (api/fetch-nodes token filekey ids)]
             (if (:error nodes-result)
               nodes-result
               (let [nodes (:ok nodes-result)]
                 {:ok (into {}
                            (for [[id {:strs [name]}] fill-entries
                                  :let [fills (get-in nodes [id "document" "fills"])]
                                  :when (seq fills)]
                              [name {:fills fills}]))})))))))))
