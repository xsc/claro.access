(ns claro.access
  (:require [claro.engine :as engine]
            [claro.data :as data]
            [claro.runtime.impl :as impl]
            [potemkin :refer [defprotocol+]]))

;; ## Protocols

(defprotocol+ Read
  "Protocol for explicit read permission handling. This will, after resolution,
   decide whether the result should be kept or replaced."
  (can-read? [resolvable env resolution-result]
    "Check whether the given resolvable is visible, based on the given
     environment value.

     The result value can be either a boolean (with `false` causing all
     resolvables to resolve to `nil`) or an error value."))

(defprotocol+ Resolve
  "Protocol for explicit resolution permission handling. This will, __before
   resolution__, decide whether the underlying resolvable should be run or not.

   This is mainly useful for mutations."
  (can-resolve? [resolvable env]
    "Check whether the given resolvable shall be resolved, based on the given
     environment value.

     The result value can be a deferred containing either a boolean (with
     `false` causing all resolvables to resolve to `nil`) or an error value."))

(extend-type Object
  Read
  (can-read? [_ _ _]
    true)

  Resolve
  (can-resolve? [_ _]
    true))

;; ## Helper

(defn- continue-or-replace
  [value continue-fn replace-fn]
  (cond (true? value)       (continue-fn)
        (false? value)      (replace-fn nil)
        (data/error? value) (replace-fn value)
        :else               (continue-fn)))

;; ## Middlewares

;; ### Resolve

(defn- group-by-resolve-access
  [impl env batch]
  (let [allow-deferreds
        (->> (for [resolvable batch
                   :let [continue-fn #(vector resolvable ::continue)
                         replace-fn  #(vector resolvable %)]]
               (impl/chain1
                 impl
                 (can-resolve? resolvable env)
                 #(continue-or-replace % continue-fn replace-fn)))
             (impl/zip impl))]
    (->> (fn [results]
           (reduce
             (fn [result [resolvable value]]
               (if (= value ::continue)
                 (update result :to-resolve conj resolvable)
                 (assoc-in result [:to-replace resolvable] value)))
             {} results))
         (impl/chain1 impl allow-deferreds))))

(defn- resolve-by-access
  [impl resolver env {:keys [to-resolve to-replace]}]
  (if-not (empty? to-resolve)
    (impl/chain
      impl
      (resolver env to-resolve)
      #(merge % to-replace))
    to-replace))

(defn wrap-resolve-access
  "Use the [[Resolve]] protocol to decide whether a batch of resolvables should
   be attempted to be resolved.

   ```clojure
   (defrecord AddToWishlist [id item-id]
     data/Mutation
     data/Resolvable
     ...

     access/Resolve
     (can-resolve? [_ {:keys [session]}]
       (d/future
         (let [{:keys [creator_id]} (fetch-wishlist! id)]
           (= (:id session) creator_id))))))
   ```

   Basically, this middleware can be used _before_ resolution to enforce
   permissions and is able to perform queries to allow for better decision
   making."
  [engine]
  (let [impl (engine/impl engine)]
    (->> (fn [resolver]
           (fn [env batch]
             (impl/chain
               impl
               (group-by-resolve-access impl env batch)
               #(resolve-by-access impl resolver env %))))
         (engine/wrap-transform engine))))

;; ### Read

(defn- replace-hidden-resolvables
  [resolvable->result env]
  (->> (for [[resolvable result] resolvable->result]
         (continue-or-replace
           (can-read? resolvable env result)
           (constantly [resolvable result])
           #(vector resolvable %)))
       (into {})))

(defn wrap-read-access
  "Use the [[Read]] protocol to decide whether the result of a resolvable's
   resolution should be returned or not.

   ```clojure
   (defrecord Wishlist [id]
     data/Resolvable
     ...

     access/Read
     (can-read? [_{:keys [session]} {:keys [creator_id]}]
       (= (:person-id session) creator_id)))
   ```

   Basically, this middleware can be used _after_ resolution to enforce read
   permissions based on the returned result."
  [engine]
  (let [impl (engine/impl engine)]
    (->> (fn [resolver]
           (fn [env batch]
             (impl/chain1
               impl
               (resolver env batch)
               #(replace-hidden-resolvables % env))))
         (engine/wrap-pre-transform engine))))

;; ### Combined Access Middleware

(defn wrap-access
  "Wrap the given engine with both [[wrap-read-access]] and
   [[wrap-resolve-access]]."
  [engine]
  (-> engine
      (wrap-read-access)
      (wrap-resolve-access)))
