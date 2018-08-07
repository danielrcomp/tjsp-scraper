(ns tjsp-scraper.scraper
  (:require [org.httpkit.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as strs])
  (:gen-class))

(def url "http://esaj.tjsp.jus.br/cpopg/search.do")

(defn get-process-params [numeracao_unica]
  "Return a hash-map representing the get params to request it's information."
  (let [numero (strs/replace numeracao_unica #"\.|-" "")
        numeroDigitoAnoUnificado (subs numero 0 13)
        foroNumeroUnificado (subs numero 16)
        ]

    hash-map {:query-params {
                             :conversationId                         ""
                             :dadosConsulta.localPesquisa.cdLocal    "-1"
                             :cbPesquisa                             "NUMPROC"
                             :dadosConsulta.tipoNuProcesso           "UNIFICADO"
                             :numeroDigitoAnoUnificado               numeroDigitoAnoUnificado
                             :forumNumeroUnificado                   foroNumeroUnificado
                             :dadosConsulta.valorConsultaNuUnificado numero
                             :dadosConsulta.valorConsulta            ""
                             }
              :insecure?    true
              }))

(defn get-dom
  "Get the dom from a page as a html/html-snippet element."
  [url numeracao_unica]
  (html/html-snippet
    (:body @(http/get url (get-process-params numeracao_unica)))))

(defn get-movs-info
  "Get the tbody the containing the content of all movs"
  [content]
  (html/select content [:tbody#tabelaTodasMovimentacoes])
  )

(defn get-trs
  "Find all trs elements from the mov"
  [movs-info]
  (html/select movs-info [:tr])
  )

(defn get-movs-content [tr-text]
  "Extract the text information from a 'movimentacao' field from the process."
  (strs/trim (get (strs/split tr-text #"[0-9]{1,2}/[0-9]{1,2}/[0-9]{1,4} ") 1))
  )

(defn get-movs-date [tr-text]
  "Find the mov date"
  (first (re-seq #"[0-9]{1,2}/[0-9]{1,2}/[0-9]{1,4}" tr-text))
  )

(defn not-blank? [str]
  "Check if a string is not blank"
  (not (strs/blank? str))
  )

(defn get-info

  "Receives a vector, a start position and a end position and return a subvector of the vector"

  ([lista start] (subvec lista start))
  ([lista start end] (subvec lista start end))

  )


(defn clean [texto]
  "Clean the text of a mov"
  (strs/replace texto #"\t|\n" ""))

(defn get-header
  "Get the header of the page"
  ([lista positions] (get-header lista positions [] 0))

  ([lista positions result x]

    ;Recursive get a list of string representing all of the information from the header of the process
   (if (not= x (- (count positions) 1) )
     (let [cont (+ x 1)
           local-result (conj result (get-info lista (nth positions x) (nth positions cont) ) )
           ]
       (recur lista positions local-result cont)
       )

     (conj result (get-info lista (last positions) ))
     ))
  )

(defn get-process-dados [dom]
  "Get the 'dados' field from a judicial process."
  (let [table (last (html/select dom [:table.secaoFormBody]))
        texts-list (strs/split (strs/trim (clean (html/text table) ) ) #"\s{2,}")
        index-elements (keep-indexed (fn [index item] [index (strs/ends-with? item ":" )]) texts-list)
        keys (filter (fn [x] (true? (last x) ) ) index-elements)
        positions (map (fn [x] (first x) ) keys)
        ]

    (get-header texts-list positions)
    )
  )


(defn get-partes-info [tr]
  "Get the information related to the 'partes' field of a judicial process."
  (let [parte-text (clean (html/text tr))
        partes-advogados-list (filterv not-blank? (strs/split parte-text #"  "))

        ]
    partes-advogados-list
    )
  )


(defn get-partes [dom]
  "Extract raw information about the 'partes' of a process and return it as list of lists."
  (let [tabela (html/select dom [:table#tableTodasPartes])
        trs (get-trs tabela)
        partes (map get-partes-info trs)
        ]
    partes
    )
  )

(defn get-movimentacoes [dom]
  "Extract the information related to the 'movimentaçõeses' field of a process."
  (let [movs-content (get-movs-info dom)
        trs-content (get-trs movs-content)
        text-content (map html/text trs-content)
        cleaned-text (map clean text-content)
        movs-date (map get-movs-date cleaned-text)
        movs-content (map get-movs-content cleaned-text)
        lista-movimentacoes (map vector movs-date movs-content)
        ]
    lista-movimentacoes
    )
  )

(defn -main [numeracao_unica]
  "Main function.
  Returns a hash-map containing 3 keys and it's values.
  The keys are 'dados' 'partes' and 'movimentações.'"
  (let [dom (get-dom url numeracao_unica)
        dados (get-process-dados dom)
        partes (get-partes dom)
        lista-movimentacoes (get-movimentacoes dom)
        dados-processo (hash-map :movimentacoes lista-movimentacoes
                                 :partes partes
                                 :dados dados)
        ]
    (println dados-processo)

    )
  )
