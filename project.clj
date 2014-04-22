(defproject anbf "0.1.0-SNAPSHOT"
  :description "A Nethack Bot Framework"
  :url "https://github.com/krajj7/ANBF"
  :license {:name "GPLv2"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.flatland/ordered "1.5.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]]
  :resource-paths ["jta26/jar/jta26.jar"]
  ;:global-vars {*warn-on-reflection* true}
  :aot [anbf.term anbf.bot]
  :main anbf.main)
