(ns sumisonic.figma-style-exporter.styles
  (:require [sumisonic.figma-style-exporter.api :as api]))

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
  "Collects all TEXT styles from a Figma file.
   Args:
     token - Figma API token string
     filekey - Figma file ID
   Returns:
     {:ok {style-name → style-map}} on success, or
     {:error {...}} on failure"
  [token filekey]
  (let [styles-meta-result (api/fetch-file-styles-meta token filekey)]
    (if (:error styles-meta-result)
      styles-meta-result
      (let [styles-meta (:ok styles-meta-result)
            txt-entries (text-style-metas styles-meta)
            local-result (fetch-local-styles token filekey txt-entries)
            remote-result (fetch-remote-styles token txt-entries)]
        (cond
          (:error local-result) local-result
          (:error remote-result) remote-result
          :else {:ok (merge (:ok local-result) (:ok remote-result))})))))

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
  "Collects all FILL (color) styles from a Figma file.
   Args:
     token - Figma API token string
     filekey - Figma file ID
   Returns:
     {:ok {style-name → {:fills [...]}} on success, or
     {:error {...}} on failure"
  [token filekey]
  (let [styles-meta-result (api/fetch-file-styles-meta token filekey)]
    (if (:error styles-meta-result)
      styles-meta-result
      (let [styles-meta (:ok styles-meta-result)
            fill-entries (filter #(= "FILL" (get-in % [1 "styleType"])) styles-meta)
            ids (map first fill-entries)
            nodes-result (api/fetch-nodes token filekey ids)]
        (if (:error nodes-result)
          nodes-result
          (let [nodes (:ok nodes-result)]
            {:ok (into {}
                       (for [[id {:strs [name]}] fill-entries
                             :let [fills (get-in nodes [id "document" "fills"])]
                             :when (seq fills)]
                         [name {:fills fills}]))}))))))
