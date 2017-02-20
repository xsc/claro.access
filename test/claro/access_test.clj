(ns claro.access-test
  (:require [clojure.test :refer :all]
            [claro.access :as access]
            [claro.data :as data]
            [claro.engine :as engine]
            [manifold.deferred :as d]))

;; ## Resolvables

(defrecord OnlyOwnerCanRead [owner-id]
  data/Resolvable
  (resolve! [this {:keys [counter]}]
    (swap! counter inc)
    (into {:fetched? true} this))
  access/Read
  (can-read? [_ {:keys [session]} {:keys [owner-id]}]
    (= (:person-id session) owner-id)))

(defrecord OnlyOwnerCanReadError [owner-id]
  data/Resolvable
  (resolve! [this {:keys [counter]}]
    (swap! counter inc)
    (into {:fetched? true} this))
  access/Read
  (can-read? [_ {:keys [session]} {:keys [owner-id]}]
    (or (= (:person-id session) owner-id)
        (data/error "only owner can read."))))

(defrecord OnlyOwnerCanResolve [owner-id]
  data/Resolvable
  (resolve! [this {:keys [counter]}]
    (swap! counter inc)
    (into {:fetched? true} this))
  access/Resolve
  (can-resolve? [_ {:keys [session]}]
    (d/future
      (= (:person-id session) owner-id))))

(defrecord OnlyOwnerCanResolveError [owner-id]
  data/Resolvable
  (resolve! [this {:keys [counter]}]
    (swap! counter inc)
    (into {:fetched? true} this))
  access/Resolve
  (can-resolve? [_ {:keys [session]}]
    (or (= (:person-id session) owner-id)
        (data/error "only owner can resolve."))))

;; ## Tests

(deftest t-wrap-read-access
  (let [run (->> (engine/engine)
                 (access/wrap-access)
                 (comp deref))
        counter (atom 0)
        env {:env {:session {:person-id :owner}
                   :counter counter}}
        input {:not-readable
               (mapv ->OnlyOwnerCanRead [:not-owner :also-not-owner])
               :readable
               (->OnlyOwnerCanRead :owner)}
        result (is (run input env))]
    (is (every? nil? (:not-readable result)))
    (is (-> result :readable :fetched?))
    (is (= 3 @counter))))

(deftest t-wrap-read-access-error-container
  (let [run (->> (engine/engine)
                 (access/wrap-access)
                 (comp deref))
        counter (atom 0)
        env {:env {:session {:person-id :owner}
                   :counter counter}}
        input {:not-readable
               (mapv ->OnlyOwnerCanReadError [:not-owner :also-not-owner])
               :readable
               (->OnlyOwnerCanRead :owner)}
        result (is (run input env))]
    (is (every? data/error? (:not-readable result)))
    (is (-> result :readable :fetched?))
    (is (= 3 @counter))))

(deftest t-wrap-resolve-access
  (let [run (->> (engine/engine)
                 (access/wrap-access)
                 (comp deref))
        counter (atom 0)
        env {:env {:session {:person-id :owner}
                   :counter counter}}
        input {:not-resolvable
               (mapv ->OnlyOwnerCanResolve [:not-owner :also-not-owner])
               :resolvable
               (->OnlyOwnerCanResolve :owner)}
        result (is (run input env))]
    (is (every? nil? (:not-resolvable result)))
    (is (-> result :resolvable :fetched?))
    (is (= 1 @counter))))

(deftest t-wrap-resolve-access-error-container
  (let [run (->> (engine/engine)
                 (access/wrap-access)
                 (comp deref))
        counter (atom 0)
        env {:env {:session {:person-id :owner}
                   :counter counter}}
        input {:not-resolvable
               (mapv ->OnlyOwnerCanResolveError [:not-owner :also-not-owner])
               :resolvable
               (->OnlyOwnerCanResolve :owner)}
        result (is (run input env))]
    (is (every? data/error? (:not-resolvable result)))
    (is (-> result :resolvable :fetched?))
    (is (= 1 @counter))))
