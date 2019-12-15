(ns spire.module.attrs
  (:require [spire.utils :as utils]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn make-script [{:keys [path owner group mode dir-mode attrs recurse]}]
  (utils/make-script
   "attrs.sh"
   {:FILE (some->> path utils/path-escape)
    :OWNER owner
    :GROUP group
    :MODE (if (number? mode) (format "%o" mode)  mode)
    :DIR_MODE (if (number? dir-mode) (format "%o" dir-mode)  dir-mode)
    :ATTRS attrs
    :RECURSE (if recurse "1" nil)}))

(defn set-attrs [session opts]
  (ssh/ssh-exec session (make-script opts) "" "UTF-8" {}))



#_
(make-script "p" "o" "g" "m" "a")

(defn get-mode-and-times [origin file]
  [(utils/file-mode file)
   (utils/last-access-time file)
   (utils/last-modified-time file)
   (str "./" (utils/relativise origin file))])

(defn create-attribute-list [file]
  (let [file (io/file file)]
    (assert (.isDirectory file) "attribute tree must be passed a directory")
    (mapv #(get-mode-and-times file %) (file-seq file))))

#_ (create-attribute-list "test/files")

(defn make-preserve-script [perm-list dest]
  (let [header (utils/embed-src "attrs_preserve.sh")
        script (concat [(format "cd %s" (utils/path-quote dest))
                        ]
                       (for [[mode access modified filename] perm-list]
                         (format
                          "set_file %o %d %s %d %s %s"
                          mode
                          access (utils/double-quote (utils/timestamp->touch access))
                          modified (utils/double-quote (utils/timestamp->touch modified))
                          (utils/path-quote filename)))
                       ["exit $EXIT"])
        script-string (string/join "\n" script)]
    (str header "\n" script-string)))

(defn set-attrs-preserve [session src dest]
  (ssh/ssh-exec
   session
   (make-preserve-script (create-attribute-list src) dest)
   "" "UTF-8" {})
  )
