(defproject claro/access "0.1.0"
  :description "Simple access control for claro resolvables."
  :url "https://github.com/xsc/claro.access"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2017
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [claro "0.2.8" :scope "provided"]]
  :profiles {:codox
             {:dependencies [[org.clojure/tools.reader "1.0.0-beta2"]
                             [codox-theme-rdash "0.1.1"]]
              :plugins [[lein-codox "0.10.3"]]
              :codox {:project {:name "claro.access"}
                      :metadata {:doc/format :markdown}
                      :themes [:rdash]
                      :source-paths ["src"]
                      :source-uri "https://github.com/xsc/claro.access/blob/master/{filepath}#L{line}"
                      :namespaces [claro.access]}}}
  :aliases {"codox" ["with-profile" "codox,dev" "codox"]}
  :pedantic? :abort)
