(ns syklotroni.converter
  (:require [clojure.string :as string]
            [clojure.java.io :as io]))

(defn convert-terms [s]
  (string/replace s #"clj-gatling" "syklotroni"))

(defn list-files-recursively [root-dir]
  (let [directory (io/file root-dir)
        dir? #(.isDirectory %)]
    (map #(.getPath %)
         (filter (comp not dir?)
                 (tree-seq dir? #(.listFiles %) directory)))))

(comment
  (let [all-files (list-files-recursively "test/syklotroni")]
    (doseq [file all-files]
      (spit file (convert-terms (slurp file)))))


  (let [file (slurp "src/clj_gatling/core.clj")]
    (convert-terms file)
    ))
