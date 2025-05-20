(ns sumisonic.figma-style-exporter.api
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

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
            params {:headers (auth-headers token),
                    :query-params {"ids" (clojure.string/join "," ids)}}
            res (http/get url params)
            body (json/parse-string (:body res))]
        {:ok (get body "nodes")})
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
