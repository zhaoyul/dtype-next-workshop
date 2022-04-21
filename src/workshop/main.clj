(ns workshop.main
  (:require
   [criterium.core :refer [quick-bench]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 1 Intro
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Prelude slides: https://tinyurl.com/usdf5twj

;; The power of the library in a nutshell.
;; We might ask: why do we need dtype-next?
;; Why not just use Clojure data structures?

(require '[tech.v3.datatype :as dtype]
         '[tech.v3.datatype.functional :as fun])

(quick-bench (reduce + (range 1000000)))
;; Execution time mean : 2.495900 ms

(quick-bench (fun/sum (dtype/->reader (range 1000000) :int64)))
;;  270.271917 µs


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 2 The Buffer in dtype-Next
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Buffers are
;; - random-access
;; - countable
;; - typed - all elements of same type
;; - **lazy & non-caching** 

;; Let's create one. A few ways to do this. Here's one.
;; We will see some other soon.
(def a-buffer (dtype/as-buffer [1 2 3 4]))
;; => #'workshop.main/a-buffer

a-buffer
;; => [1 2 3 4]

;; Random access
(nth a-buffer 2)
;; => 3


;; Countable
(count a-buffer)
;; => 4

;; Interesting that it prints as a regular persistent vector.
;; What if check its class?
(class a-buffer)
;; => tech.v3.datatype.base$random_access$reify__11693

;; Wow! That's unusual. What's going on here?  

;; How can we know what type of thing we are working with then?
(dtype/datatype [1 2 3])
;; => :persistent-vector

(dtype/datatype a-buffer)
;; => :buffer

(dtype/elemwise-datatype a-buffer)
;; => :object

;; Hmmm why object? What's happening here? 

(dtype/elemwise-datatype (dtype/as-buffer (int-array [1 2 3])))
;; => :int32
(dtype/elemwise-datatype (dtype/as-buffer (vector-of :int 1 2 3)))
;; => :object

;; Better yet some pathways in dtype-next for making things with specific types.

(def an-int-buffer (dtype/->reader [1 2 3] :int32))
;; => #'workshop.main/an-int-buffer

an-int-buffer

(dtype/elemwise-datatype an-int-buffer)
;; => :int32

(tech.v3.datatype.casting/all-datatypes)
;; => (:int32 :int16 :float32 :float64 :int64 :uint64 :string :uint16 :int8 :uint32 :keyword :uuid :boolean :object :char :uint8)

;; What is this thing the reader?

;; In fact there are two kinds of buffers in dtype-next
;; - A reader buffer - we can read values
;; - A writer buffer - we can write (i.e. mutate values)

;; You cannot write to a reader

(dtype/set-value! (dtype/->reader [1 2 3]) 1 0)


(dtype/set-value! (dtype/->writer (int-array [1 2 3])) 1 0)
;; => [1 0 3]

;; Lazy & Non-caching

(def big-rdr (dtype/make-reader :int64 1000000 (* idx (rand-int 100))))
;; => #'workshop.main/big-rdr

(take 5 big-rdr)
;; => (0 88 86 12 316)

(dtype/sub-buffer big-rdr 999000 5)
;; => [74925000 21978022 25974052 66933201 59940240]

(def realized-br (dtype/make-container big-rdr)) ;; also: `dtype/clone`
;; => #'workshop.main/realized-br

(dtype/datatype big-rdr)
;; => :buffer

(dtype/elemwise-datatype big-rdr)
;; => :int64

(dtype/datatype realized-br)
;; => :array-buffer

(dtype/elemwise-datatype realized-br)
;; => :int64

(take 5 realized-br)
;; => (0 84 194 12 28)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 3 Working with Buffers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; So we now know what buffers are. How do we interact with them?
;; We can often use clojure functions like map & reduce.
(reduce + (dtype/->reader (range 1000000) :int32))
;; => 499999500000

(keep #(when (odd? %) %) (dtype/->reader (range 10) :int32))
;; => (1 3 5 7 9)

(count (dtype/->reader (range 1000000) :int32))
;; => 1000000

;; But we can't expect the operations to be particular efficient
;; and we leave dtype-next world when we use them.

(class
 (keep #(when (odd? %) %) (dtype/->reader (range 10) :int32)))
;; => clojure.lang.LazySeq


;; Most of the time we want to use dtype-next's "functional"
;; namespace: tech.v3.datatype.functional. Usually aliased as `fun`.
;; This namespace provides a number of arithemetic operations that
;; will return a buffer!
;;
;; https://cnuernber.github.io/dtype-next/tech.v3.datatype.functional.html

;; With this namespace we can perform basic arithemetic on buffers.
(def a (dtype/->reader [20 30 40 50] :int32))
;; => #'workshop.main/a

(def b (dtype/->reader (range 4) :int32))
;; => #'workshop.main/b

(fun/- a b)
;; => [20 29 38 47]
(fun/- a 2)
;; => [18 28 38 48]
(fun/- 3 2)
;; => 1

(dtype/datatype (fun/- a b))
;; => :buffer

(fun/pow a 2)
;; => [400.0 900.0 1600.0 2500.0]

(fun/log a)
;; => [2.995732273553991 3.4011973816621555 3.6888794541139363 3.912023005428146]

;; Upcasting - dtype-next will upcast
(def a-ints (dtype/->reader (range 10) :int32))
;; => #'workshop.main/a-ints

(def b-floats (dtype/make-reader :float32 10 (rand 10)))
;; => #'workshop.main/b-floats

(dtype/elemwise-datatype a-ints)
;; => :int32

(dtype/elemwise-datatype b-floats)
;; => :float32

;; What do we expect here?
(fun/* a-ints b-floats)
;; => [0.0 4.875691966470928 11.79193246434785 4.487966887979593 28.949245242219014 26.57515217004065 31.78978360441407 4.357204497947179 47.238764174988916 30.555691523286292]

(dtype/elemwise-datatype (fun/* a-ints b-floats))
;; => :float64

;; Mapping
(dtype/emap (constantly 0) :int64 (range 10))
;; => [0 0 0 0 0 0 0 0 0 0]

(dtype/emap (fn [x] (+ x (/ x 10))) :float64 (range 10))
;; => [0.0 1.1 2.2 3.3 4.4 5.5 6.6 7.7 8.8 9.9]

;; Subsetting
(dtype/sub-buffer (dtype/->reader (range 10) :int64) 5 3)
;; => [5 6 7]

;; Filtering in index space

;; Let's say we want to grab only odd values?
(fun/odd? (dtype/->reader (range 10) :int32))
;; => [false true false true false true false true false true]

(require '[tech.v3.datatype.argops :as dtype-argops])

(dtype-argops/argfilter odd? (dtype/->reader (range 100)))
;; => #list<int32>[50]
[1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39...]

(let [rdr (dtype/->reader (range 100) :int32)
      indices (dtype-argops/argfilter odd? rdr)]
  (dtype/indexed-buffer indices rdr))
;; => [1 3 5 7 9 11 13 15 17 19 21 23 25 27 29 31 33 35 37 39 41 43 45 47 49 51 53 55 57 59 61 63 65 67 69 71 73 75 77 79 81 83 85 87 89 91 93 95 97 99]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 3 Small exercise to put this together
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Goal: Create a normalized form of iris's sepal length whose values
;; range exactly between 0 and 1 so that the minimum has value 0 and
;; maximum has value 1.

(def data-url
  "https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data" )

(require '[clojure.data.csv :as csv])

(def raw-data (-> data-url slurp csv/read-csv))

(take 2 raw-data)

(def data (->> (vec raw-data)
               (dtype/emap first :object)
               (dtype/emap #(Float/parseFloat %) :float32)))

(take 5 data)
;; => (5.099999904632568 4.900000095367432 4.699999809265137 4.599999904632568 5.0)

(dtype/elemwise-datatype data)
;; => :float32

(let [smin (fun/reduce-min data)
      smax (fun/reduce-max data)]
  (fun// (fun/- data smin) (- smax smin)))

;; ☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠☠

(def bad-data (->> (vec raw-data)
               (dtype/emap first :object)
               #_(dtype/emap #(Float/parseFloat %) :float32)))

;; We see we have one empty string
(dtype-argops/argfilter #(= % "") bad-data)

;; So let's clean the data using the index-space operation
;; from above (e.g. `argfilter`)

(defn clean [rdr]
  (let [indices (dtype-argops/argfilter (complement #(= % "")) rdr)]
    (dtype/indexed-buffer indices rdr)))

(def good-data (->> (vec raw-data)
                    (dtype/emap first :object)
                    (clean)
                    (dtype/emap #(Float/parseFloat %) :float32)))

(dtype-argops/argfilter #(= % "") good-data)
;; => #list<int32>[0]
[]

(def normalized-data
  (let [smin (fun/reduce-min good-data)
        smax (fun/reduce-max good-data)]
    (fun// (fun/- good-data smin) (- smax smin))))
;; => #'workshop.main/normalized-data

normalized-data

;; Validate result
(fun/sum (dtype/concat-buffers
          :int32
          [(dtype/->int-array (fun/> 1 good-data))
           (dtype/->int-array (fun/< good-data 0))]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 4 Addendum: Illustrating the connection bewteen dtype-next and tech.ml.dataset
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(require '[tech.v3.dataset :as tmd])

(tmd/->dataset data-url)


(tmd/->dataset data-url {:file-type :csv})
;; => https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data [150 5]:

| 5.1 | 3.5 | 1.4 | 0.2 |    Iris-setosa |
|----:|----:|----:|----:|----------------|
| 4.9 | 3.0 | 1.4 | 0.2 |    Iris-setosa |
| 4.7 | 3.2 | 1.3 | 0.2 |    Iris-setosa |
| 4.6 | 3.1 | 1.5 | 0.2 |    Iris-setosa |
| 5.0 | 3.6 | 1.4 | 0.2 |    Iris-setosa |
| 5.4 | 3.9 | 1.7 | 0.4 |    Iris-setosa |
| 4.6 | 3.4 | 1.4 | 0.3 |    Iris-setosa |
| 5.0 | 3.4 | 1.5 | 0.2 |    Iris-setosa |
| 4.4 | 2.9 | 1.4 | 0.2 |    Iris-setosa |
| 4.9 | 3.1 | 1.5 | 0.1 |    Iris-setosa |
| 5.4 | 3.7 | 1.5 | 0.2 |    Iris-setosa |
| ... | ... | ... | ... |            ... |
| 6.7 | 3.1 | 5.6 | 2.4 | Iris-virginica |
| 6.9 | 3.1 | 5.1 | 2.3 | Iris-virginica |
| 5.8 | 2.7 | 5.1 | 1.9 | Iris-virginica |
| 6.8 | 3.2 | 5.9 | 2.3 | Iris-virginica |
| 6.7 | 3.3 | 5.7 | 2.5 | Iris-virginica |
| 6.7 | 3.0 | 5.2 | 2.3 | Iris-virginica |
| 6.3 | 2.5 | 5.0 | 1.9 | Iris-virginica |
| 6.5 | 3.0 | 5.2 | 2.0 | Iris-virginica |
| 6.2 | 3.4 | 5.4 | 2.3 | Iris-virginica |
| 5.9 | 3.0 | 5.1 | 1.8 | Iris-virginica |
|     |     |     |     |                |


(tmd/->dataset data-url {:file-type :csv :header-row? false})
;; => https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data [151 5]:

| column-0 | column-1 | column-2 | column-3 |       column-4 |
|---------:|---------:|---------:|---------:|----------------|
|      5.1 |      3.5 |      1.4 |      0.2 |    Iris-setosa |
|      4.9 |      3.0 |      1.4 |      0.2 |    Iris-setosa |
|      4.7 |      3.2 |      1.3 |      0.2 |    Iris-setosa |
|      4.6 |      3.1 |      1.5 |      0.2 |    Iris-setosa |
|      5.0 |      3.6 |      1.4 |      0.2 |    Iris-setosa |
|      5.4 |      3.9 |      1.7 |      0.4 |    Iris-setosa |
|      4.6 |      3.4 |      1.4 |      0.3 |    Iris-setosa |
|      5.0 |      3.4 |      1.5 |      0.2 |    Iris-setosa |
|      4.4 |      2.9 |      1.4 |      0.2 |    Iris-setosa |
|      4.9 |      3.1 |      1.5 |      0.1 |    Iris-setosa |
|      ... |      ... |      ... |      ... |            ... |
|      6.7 |      3.1 |      5.6 |      2.4 | Iris-virginica |
|      6.9 |      3.1 |      5.1 |      2.3 | Iris-virginica |
|      5.8 |      2.7 |      5.1 |      1.9 | Iris-virginica |
|      6.8 |      3.2 |      5.9 |      2.3 | Iris-virginica |
|      6.7 |      3.3 |      5.7 |      2.5 | Iris-virginica |
|      6.7 |      3.0 |      5.2 |      2.3 | Iris-virginica |
|      6.3 |      2.5 |      5.0 |      1.9 | Iris-virginica |
|      6.5 |      3.0 |      5.2 |      2.0 | Iris-virginica |
|      6.2 |      3.4 |      5.4 |      2.3 | Iris-virginica |
|      5.9 |      3.0 |      5.1 |      1.8 | Iris-virginica |
|          |          |          |          |                |



(def ds (tmd/->dataset data-url {:file-type :csv :header-row? false}))
;; => #'workshop.main/ds

(count ds)
;; => 5
(tmd/row-count ds)
;; => 151

;;    Confirm column labels
(tmd/head ds)
;; => https://archive.ics.uci.edu/ml/machine-learning-databases/iris/iris.data [5 5]:

| column-0 | column-1 | column-2 | column-3 |    column-4 |
|---------:|---------:|---------:|---------:|-------------|
|      5.1 |      3.5 |      1.4 |      0.2 | Iris-setosa |
|      4.9 |      3.0 |      1.4 |      0.2 | Iris-setosa |
|      4.7 |      3.2 |      1.3 |      0.2 | Iris-setosa |
|      4.6 |      3.1 |      1.5 |      0.2 | Iris-setosa |
|      5.0 |      3.6 |      1.4 |      0.2 | Iris-setosa |


(dtype/->reader (ds "column-0") :float64)
;; => [5.1 4.9 4.7 4.6 5.0 5.4 4.6 5.0 4.4 4.9 5.4 4.8 4.8 4.3 5.8 5.7 5.4 5.1 5.7 5.1 5.4 5.1 4.6 5.1 4.8 5.0 5.0 5.2 5.2 4.7 4.8 5.4 5.2 5.5 4.9 5.0 5.5 4.9 4.4 5.1 5.0 4.5 4.4 5.0 5.1 4.8 5.1 4.6 5.3 5.0 7.0 6.4 6.9 5.5 6.5 5.7 6.3 4.9 6.6 5.2 5.0 5.9 6.0 6.1 5.6 6.7 5.6 5.8 6.2 5.6 5.9 6.1 6.3 6.1 6.4 6.6 6.8 6.7 6.0 5.7 5.5 5.5 5.8 6.0 5.4 6.0 6.7 6.3 5.6 5.5 5.5 6.1 5.8 5.0 5.6 5.7 5.7 6.2 5.1 5.7 6.3 5.8 7.1 6.3 6.5 7.6 4.9 7.3 6.7 7.2 6.5 6.4 6.8 5.7 5.8 6.4 6.5 7.7 7.7 6.0 6.9 5.6 7.7 6.3 6.7 7.2 6.2 6.1 6.4 7.2 7.4 7.9 6.4 6.3 6.1 7.7 6.3 6.4 6.0 6.9 6.7 6.9 5.8 6.8 6.7 6.7 6.3 6.5 6.2 5.9 nil]

(def sepal (dtype/->reader (ds "column-0")))
;; => #'workshop.main/sepal
(dtype/elemwise-datatype sepal)
;; => :float64

sepal
(class sepal)
;; => tech.v3.dataset.impl.column$make_buffer$reify__18634
(dtype/datatype sepal)
;; => :buffer

(def column (ds "column-0"))
;; => #'workshop.main/column
(dtype/elemwise-datatype column)
;; => :float64
column

(.data column)
;; => #array-buffer<float64>[151]
[5.100, 4.900, 4.700, 4.600, 5.000, 5.400, 4.600, 5.000, 4.400, 4.900, 5.400, 4.800, 4.800, 4.300, 5.800, 5.700, 5.400, 5.100, 5.700, 5.100...]

(fun/+ sepal 10)
;; => [15.1 14.9 14.7 14.6 15.0 15.4 14.6 15.0 14.4 14.9 15.4 14.8 14.8 14.3 15.8 15.7 15.4 15.1 15.7 15.1 15.4 15.1 14.6 15.1 14.8 15.0 15.0 15.2 15.2 14.7 14.8 15.4 15.2 15.5 14.9 15.0 15.5 14.9 14.4 15.1 15.0 14.5 14.4 15.0 15.1 14.8 15.1 14.6 15.3 15.0 17.0 16.4 16.9 15.5 16.5 15.7 16.3 14.9 16.6 15.2 15.0 15.9 16.0 16.1 15.6 16.7 15.6 15.8 16.2 15.6 15.9 16.1 16.3 16.1 16.4 16.6 16.8 16.7 16.0 15.7 15.5 15.5 15.8 16.0 15.4 16.0 16.7 16.3 15.6 15.5 15.5 16.1 15.8 15.0 15.6 15.7 15.7 16.2 15.1 15.7 16.3 15.8 17.1 16.3 16.5 17.6 14.9 17.3 16.7 17.2 16.5 16.4 16.8 15.7 15.8 16.4 16.5 17.7 17.7 16.0 16.9 15.6 17.7 16.3 16.7 17.2 16.2 16.1 16.4 17.2 17.4 17.9 16.4 16.3 16.1 17.7 16.3 16.4 16.0 16.9 16.7 16.9 15.8 16.8 16.7 16.7 16.3 16.5 16.2 15.9 ##NaN]
(fun/+ column 10)
;; => [15.1 14.9 14.7 14.6 15.0 15.4 14.6 15.0 14.4 14.9 15.4 14.8 14.8 14.3 15.8 15.7 15.4 15.1 15.7 15.1 15.4 15.1 14.6 15.1 14.8 15.0 15.0 15.2 15.2 14.7 14.8 15.4 15.2 15.5 14.9 15.0 15.5 14.9 14.4 15.1 15.0 14.5 14.4 15.0 15.1 14.8 15.1 14.6 15.3 15.0 17.0 16.4 16.9 15.5 16.5 15.7 16.3 14.9 16.6 15.2 15.0 15.9 16.0 16.1 15.6 16.7 15.6 15.8 16.2 15.6 15.9 16.1 16.3 16.1 16.4 16.6 16.8 16.7 16.0 15.7 15.5 15.5 15.8 16.0 15.4 16.0 16.7 16.3 15.6 15.5 15.5 16.1 15.8 15.0 15.6 15.7 15.7 16.2 15.1 15.7 16.3 15.8 17.1 16.3 16.5 17.6 14.9 17.3 16.7 17.2 16.5 16.4 16.8 15.7 15.8 16.4 16.5 17.7 17.7 16.0 16.9 15.6 17.7 16.3 16.7 17.2 16.2 16.1 16.4 17.2 17.4 17.9 16.4 16.3 16.1 17.7 16.3 16.4 16.0 16.9 16.7 16.9 15.8 16.8 16.7 16.7 16.3 16.5 16.2 15.9 ##NaN]


