(ns trombi.converter
  (:require [clojure.string :as string]
            [clojure.java.io :as io]))

(defn convert-terms [s]
  (string/replace s #"trombi" "trombi"))

(defn list-files-recursively [root-dir]
  (let [directory (io/file root-dir)
        dir? #(.isDirectory %)]
    (map #(.getPath %)
         (filter (comp not dir?)
                 (tree-seq dir? #(.listFiles %) directory)))))

(comment
  (let [all-files (list-files-recursively "src/clj_gatling")]
    (doseq [file all-files]
      (spit file (convert-terms (slurp file)))))


  (let [file (slurp "src/clj_gatling/core.clj")]
    (convert-terms file)
    ))
