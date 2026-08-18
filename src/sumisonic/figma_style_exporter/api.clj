(ns sumisonic.figma-style-exporter.api
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def nodes-batch-size
  "Maximum number of node IDs sent in a single /nodes request.

   The Figma API rejects long query strings with HTTP 414 (URI Too Long).
   Measured against a real file: 713 ids (~7.5KB of query) fails, 200 ids (~2KB)
   succeeds. 100 keeps a wide safety margin and still keeps the request count low."
  100)

(defn batch-ids
  "Splits node IDs into batches that keep each request's query string short enough.
   Args:
     ids - Sequence of node IDs
   Returns:
     Sequence of ID batches (each at most nodes-batch-size long)"
  [ids]
  (partition-all nodes-batch-size ids))

(defn auth-headers
  "Creates authorization headers for Figma API requests.
   Args:
     token - Figma API token string
   Returns:
     A map containing the required authorization headers"
  [token]
  {"X-FIGMA-TOKEN" token,
   "Accept" "application/json"})

(defn fetch-file-styles-meta
  "Fetches style metadata from a Figma file.
   Args:
     token - Figma API token string
     filekey - Figma file ID/key
   Returns:
     {:ok styles-map} on success, or
     {:error error-details} on failure"
  [token filekey]
  (try
    (let [url (str "https://api.figma.com/v1/files/" filekey)
          res (http/get url {:headers (auth-headers token)})
          body (json/parse-string (:body res))]
      {:ok (get body "styles")})
    (catch Exception e
      {:error {:type :api-error,
               :message (.getMessage e),
               :function :fetch-file-styles-meta}})))

(defn fetch-nodes
  "Fetches specified nodes from a Figma file in batch.
   Args:
     token - Figma API token string
     filekey - Figma file ID/key
     ids - Sequence of node IDs to fetch
   Returns:
     {:ok nodes-map} on success, or
     {:error error-details} on failure"
  [token filekey ids]
  (if (seq ids)
    (try
      (let [url (str "https://api.figma.com/v1/files/" filekey "/nodes")
            fetch-batch (fn [batch]
                          (let [params {:headers (auth-headers token),
                                        :query-params {"ids" (str/join "," batch)}}
                                res (http/get url params)
                                body (json/parse-string (:body res))]
                            (get body "nodes")))]
        ;; Split into batches so the query string stays under the API's URI length limit.
        {:ok (reduce merge {} (map fetch-batch (batch-ids ids)))})
      (catch Exception e
        {:error {:type :api-error,
                 :message (.getMessage e),
                 :function :fetch-nodes}}))
    {:ok {}}))

(defn fetch-style
  "Fetches details for a specific style by its key.
   Args:
     token - Figma API token string
     key - Style key to fetch
   Returns:
     {:ok style-map} on success, or
     {:error error-details} on failure"
  [token key]
  (try
    (let [url (str "https://api.figma.com/v1/styles/" key)
          res (http/get url {:headers (auth-headers token)})
          body (json/parse-string (:body res))]
      {:ok (get body "style")})
    (catch Exception e
      {:error {:type :api-error,
               :message (.getMessage e),
               :function :fetch-style,
               :key key}})))
