# Iskalnik programskih vmesnikov

Iskalnik programskih vmesnikov je sestavljen iz dveh storitev, zbiratelja in iskalnika. Zbiratelj je napisan v Kotlinu, iskalnik pa v Pythonu. V mapi `Podatki` so rezultati iskanja z nizem in izbiro skupin. V podmapah je podrobneje opisan njihov format.

## Zbiratelj podatkov

Zbira podatke iz različnih virov in jih shranjuje v Elasticsearch in relacijsko podatkovno bazo.
Podrobnejši opis zbiratelja se nahaja v tretjem poglavju magistrskega dela. 

## Iskalnik

Vsebuje implementacijo iskalnika z nizem in izbiro skupin ter kodo, potrebno za izris vizualizacij.
Večino kode za izračun podobnosti (četrto poglavje magistrskega dela) in iskanje z nizem (peto poglavje magistrskega dela) se nahaja v datoteki `wordnet_similarity.py`, evalvacija metode pa v datoteki `evaluator.py`.
Implementacijo metode z izbiro skupin (šesto poglavje) lahko najdemo v datoteki `wordnet_similarity.py`, njeno evalvacijo pa v `group_search_evaluator.py`.