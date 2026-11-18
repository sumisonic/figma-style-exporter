(ns sumisonic.figma-style-exporter.fake-figma
  "A stand-in for the Figma API, so tests can run a whole export without a network.
   It serves a fixed set of styles and records which nodes and style keys were requested."
  (:require [sumisonic.figma-style-exporter.api :as api]))

(defn text-props
  "Text style properties as the /nodes endpoint returns them."
  [font-size]
  {"fontFamily" "Inter",
   "fontPostScriptName" "Inter-Regular",
   "fontWeight" 400,
   "fontSize" font-size,
   "letterSpacing" 0.0,
   "lineHeightPx" (* font-size 1.5)})

(defn solid-fills
  "A fills list holding one solid color."
  [r g b]
  [{"type" "SOLID", "color" {"r" r, "g" g, "b" b, "a" 1.0}}])

(def single-shape-file
  "Text styles that all follow one name shape (breakpoint/name), plus a remote one."
  [{:id "1:1", :name "sm/Body", :type "TEXT", :props (text-props 14.0)}
   {:id "1:2", :name "md/Body", :type "TEXT", :props (text-props 16.0)}
   {:id "1:3", :name "sm/Heading", :type "TEXT", :props (text-props 24.0)}
   {:id "1:4", :name "md/Heading", :type "TEXT", :props (text-props 32.0)}
   {:id "9:1", :name "sm/Caption", :type "TEXT", :remote true}])

(def two-years-file
  "Styles from two years in one file. The 2026 styles start with \"2026/\".
   Some older styles start with \"2025/\", and some have no year at all."
  [{:id "1:1", :name "2026/sm/Body", :type "TEXT", :props (text-props 14.0)}
   {:id "1:2", :name "2026/md/Body", :type "TEXT", :props (text-props 16.0)}
   {:id "1:3", :name "2025/sm/Body", :type "TEXT", :props (text-props 13.0)}
   {:id "1:4", :name "sm/Body", :type "TEXT", :props (text-props 12.0)}
   {:id "9:1", :name "2026/sm/Caption", :type "TEXT", :remote true}
   {:id "9:2", :name "2025/sm/Caption", :type "TEXT", :remote true}
   {:id "2:1", :name "2026/Primary", :type "FILL", :fills (solid-fills 0.1 0.2 0.3)}
   {:id "2:2", :name "2025/Primary", :type "FILL", :fills (solid-fills 0.4 0.5 0.6)}
   {:id "2:3", :name "Primary", :type "FILL", :fills (solid-fills 0.7 0.8 0.9)}])

(defn- style-key
  [id]
  (str "key-" id))

(defn call-with-file
  "Calls f while the Figma API functions serve the styles in file.
   Args:
     file - Sequence of style maps (:id :name :type, and :props, :fills or :remote)
     f - Function of no arguments that runs the code under test
   Returns:
     {:result value-of-f, :node-ids [ids sent to /nodes], :style-keys [keys sent to /styles]}"
  [file f]
  (let [node-ids (atom [])
        style-keys (atom [])
        meta (into {}
                   (for [{:keys [id name type remote]} file]
                     [id {"key" (style-key id),
                          "name" name,
                          "styleType" type,
                          "remote" (boolean remote)}]))
        documents (into {}
                        (for [{:keys [id props fills]} file]
                          [id {"document" (cond-> {}
                                            props (assoc "style" props)
                                            fills (assoc "fills" fills))}]))
        ;; /v1/styles/:key returns style metadata, not the text properties.
        remote-styles (into {}
                            (for [{:keys [id name remote]} file
                                  :when remote]
                              [(style-key id) {"key" (style-key id), "name" name}]))]
    (with-redefs [api/fetch-file-styles-meta (fn [_ _] {:ok meta})
                  api/fetch-nodes (fn [_ _ ids]
                                    (swap! node-ids into ids)
                                    {:ok (select-keys documents ids)})
                  api/fetch-style (fn [_ key]
                                    (swap! style-keys conj key)
                                    {:ok (get remote-styles key)})]
      (let [result (f)]
        {:result result,
         :node-ids @node-ids,
         :style-keys @style-keys}))))

(defn call-with-api-error
  "Calls f while the first Figma API request fails.
   Returns:
     The value of f"
  [f]
  (with-redefs [api/fetch-file-styles-meta
                (fn [_ _]
                  {:error {:type :api-error,
                           :message "clj-http: status 403",
                           :function :fetch-file-styles-meta}})]
    (f)))

(defn call-with-nodes-error
  "Calls f while the style list of file is served but every /nodes request fails.
   Returns:
     The value of f"
  [file f]
  (:result
   (call-with-file file
                   #(with-redefs [api/fetch-nodes
                                  (fn [_ _ _]
                                    {:error {:type :api-error,
                                             :message "clj-http: status 500",
                                             :function :fetch-nodes}})]
                      (f)))))
