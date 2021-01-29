(ns next.cljs.shadow-test
  (:require [clojure.test :refer [deftest is testing]]
            [next.cljs.shadow :refer [create-pages
                                      flush-files
                                      relative-import-path
                                      write-file]]))

(deftest relative-import-path-test
  (testing "finds the relative import path from one file to another"
    (is (= "../cljs/hello.js"
           (relative-import-path "src/pages/hello.js" "src/cljs/hello.js")))))

(deftest create-pages-test
  (testing "Raises an error if a var is missing :next.cljs/export-as"
    (let [vars [{:name 'some-namespace.core/page-1
                 :meta {:next.cljs/page "some/page"}}
                {:name 'some-namespace.core/some-func
                 :meta {:next.cljs/page "some/page"
                        :next.cljs/export-as "getStaticProps"}}
                {:name 'some-namespace.other/page-2
                 :meta {:next.cljs/page "some/other/page"
                        :next.cljs/export-as "default"}}]]
      (is (thrown?
           IllegalArgumentException
           (doall (create-pages "output" "pages" vars))))))
  (testing "Returns a file with exports for vars with :next.cljs/page metadata"
    (let [vars [{:name 'some-namespace.core/page-1
                 :meta {:next.cljs/page "some/page"
                        :next.cljs/export-as "default"}}
                {:name 'some-namespace.core/some-func
                 :meta {:next.cljs/page "some/page"
                        :next.cljs/export-as "getStaticProps"}}
                {:name 'some-namespace.other/page-2
                 :meta {:next.cljs/page "some/other/page"
                        :next.cljs/export-as "default"}}]
          expected [{:path "src/pages/some/page.js"
                     :content "export {page_1 as default, some_func as getStaticProps} from '../../out/some_namespace.core.js';"}
                    {:path "src/pages/some/other/page.js"
                     :content "export {page_2 as default} from '../../../out/some_namespace.other.js';"}]
          actual (create-pages "src/out" "src/pages" vars)]
      (is (= expected actual)))))

(deftest flush-files-test
  (testing "Raises an error if one of the passed-in files does not conform to the expected interface."
    (with-redefs [write-file (fn [& args] args)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (flush-files [{:wrong "wrong"}]))))))
