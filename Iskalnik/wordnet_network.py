from operator import itemgetter

import networkx as nx
import matplotlib.pyplot as plt
from pyvis.network import Network
import community as community_louvain

from api_doc_transformer import transform_api_to_document
from colors import get_different_colors
from database import database_info, service_info
from elasticsearch_client import get_services_from_table, get_all_endpoints, get_parameters, get_responses
from git_network import partition_git_network, get_service_community_neighbours
from string_utils import document_from_database_info
from tfidf_kmeans import tfidf_vectorizer
from wordnet_similarity import similarity_network, get_weighted_synset_tfidf_docs, get_enriched_endpoints
from collections import Counter
from karateclub import EgoNetSplitter


def api_docs_network():
    ee = get_enriched_endpoints()
    endpoints = [e['endpoint'] for e in ee]
    similarity_matrix = similarity_network([e['document'] for e in ee], weighted=True, enr_endpoints=ee)

    similarity_threshold = 0.7
    colors = get_different_colors(305)
    added_nodes = []
    wordnet_network = nx.Graph()
    vis_network = Network(height="100%", width="70%")
    for iy, y in enumerate(similarity_matrix):
        for ix, x in enumerate(y):
            if x > similarity_threshold and ix != iy:
                y_name = endpoints[iy]['path']
                x_name = endpoints[ix]['path']
                if y_name not in added_nodes:
                    vis_network.add_node(y_name)
                    # vis_network.add_node(y_name,
                    #                      color="#FF0000" if len(get_services_from_table(y_name)) > 0 else "#DDDDDD")
                    added_nodes.append(y_name)
                if x_name not in added_nodes:
                    vis_network.add_node(x_name)
                    # vis_network.add_node(x_name,
                    #                      color="#FF0000" if len(get_services_from_table(y_name)) > 0 else "#DDDDDD")
                    added_nodes.append(x_name)

                # norm_weight = (x - similarity_threshold) / (1 - similarity_threshold)
                vis_network.add_edge(y_name, x_name)
                wordnet_network.add_edge(y_name, x_name, weight=x)

    nx.write_gexf(wordnet_network, "network_outputs/api_wordnet.gexf")
    nx.draw_networkx(wordnet_network, node_size=3, with_labels=False)
    plt.show()
    vis_network.show_buttons(filter_=['physics'])
    vis_network.show("network_outputs/api_wordnet.html")


def get_wordnet_labels(documents):
    similarity_matrix = similarity_network(documents)
    similarity_threshold = 0.3

    wordnet_network = nx.Graph()
    for iy, y in enumerate(similarity_matrix):
        for ix, x in enumerate(y):
            if x > similarity_threshold and ix != iy:
                wordnet_network.add_edge(iy, ix, weight=x)
    partition = community_louvain.best_partition(wordnet_network, random_state=1)
    return [partition.get(x, max(partition.values()) + 1) for x in range(len(documents))]


def word_feature_filter(words, count):
    return [w for w in words if
            len(w) > 2 and w not in ['date', 'com', 'api', 'pom', 'created', 'timestamp']][
           :count]


def partition_network(network, documents, node_name_map):
    # splitter = EgoNetSplitter(1.0)
    #
    # splitter.fit(network)
    #
    # memberships = splitter.get_memberships()
    # print(memberships)
    partition = community_louvain.best_partition(network, random_state=1)

    # transforms to map with partition id as key and list of tables as value
    partitions_tables = {}
    for key, value in sorted(partition.items()):
        partitions_tables.setdefault(value, []).append(key)

    partition_doc_dict = {}
    partitions_services = {}
    all_services = set()
    for partition, tables in partitions_tables.items():
        # joins all documents of tables in same partition and puts it to map
        # with partition id as key joined document as value
        partition_doc = ''
        for table_name in tables:
            partition_doc += ' ' + documents[table_name]
        partition_doc_dict[partition] = partition_doc

        # gets services of tables in partition
        partition_all_services = set()
        for t in tables:
            partition_all_services.update(get_services_from_table(t))
        all_services.update(partition_all_services)
        partitions_services[partition] = sorted(list(partition_all_services))

    for partition, services in sorted(partitions_services.items()):
        print("Partition {}: Found {} services: {}".format(str(partition), str(len(services)), str(sorted(services))))
    # gets tfidf matrix, feature names and dictionary of stems and corresponding words
    tfidf, feature_names, stem_dictionaries = tfidf_vectorizer(partition_doc_dict.values())
    # gets top words for each partition (gets top stems and finds word which is most frequent for that stem)
    partition_words = {}
    for i, d in enumerate(tfidf):
        important_stems = sorted(zip(feature_names, d.toarray().tolist()[0]), key=itemgetter(1), reverse=True)[:10]
        important_words = [Counter(stem_dictionaries[i][stem[0]]).most_common()[0][0] for stem in important_stems]
        partition_words[list(partition_doc_dict.keys())[i]] = word_feature_filter(important_words, 3)
    return all_services, partitions_services, partition_words


def partition_endpoints(enriched_endpoints):
    documents = [e['document'] for e in enriched_endpoints]
    similarity_matrix = similarity_network(documents, weighted=True, enr_endpoints=enriched_endpoints)
    endpoint_id_document_map = dict(zip([e['endpoint']['id'] for e in enriched_endpoints], documents))
    endpoint_id_endpoint_map = dict(zip([e['endpoint']['id'] for e in enriched_endpoints], enriched_endpoints))

    similarity_threshold = 0.4
    wordnet_network = nx.Graph()
    for iy, y in enumerate(similarity_matrix):
        for ix, x in enumerate(y):
            if x > similarity_threshold and ix != iy:
                y_name = enriched_endpoints[iy]['endpoint']['id']
                x_name = enriched_endpoints[ix]['endpoint']['id']
                wordnet_network.add_edge(y_name, x_name, weight=x)
    partition = community_louvain.best_partition(wordnet_network, random_state=11)
    size = len(set(partition.values()))

    # transforms to map with partition id as key and list of tables as value
    partitions_endpoint_ids = {}
    for key, value in sorted(partition.items()):
        partitions_endpoint_ids.setdefault(value, []).append(key)

    partition_doc_dict = {}
    partitions_endpoints = {}
    for partition, endpoint_ids in partitions_endpoint_ids.items():
        partition_doc = []
        for e_id in endpoint_ids:
            partition_doc += endpoint_id_document_map[e_id]
        partition_doc_dict[partition] = partition_doc

        # gets services of tables in partition
        print("Partition " + str(partition))
        endpoints_of_partition = [endpoint_id_endpoint_map[x] for x in endpoint_ids]
        partitions_endpoints[partition] = endpoints_of_partition
        print("Found " + str(len(endpoints_of_partition)) + " endpoints: " + str(
            sorted([x['endpoint']['path'] for x in endpoints_of_partition])))

    synsets_tfidfs_docs, lemma_dictionaries, stem_dictionaries = get_weighted_synset_tfidf_docs(
        partition_doc_dict.values())
    # gets top words for each partition (gets top stems and finds word which is most frequent for that stem)
    partition_words = {}
    for i, d in enumerate(synsets_tfidfs_docs):
        important_lemmas_stems = sorted(d, key=itemgetter(2), reverse=True)[:10]
        important_words = []
        for ils in important_lemmas_stems:
            if ils[0] and ils[0] in lemma_dictionaries[i]:
                important_words.append(Counter(lemma_dictionaries[i][ils[0]]).most_common()[0][0])
            elif ils[1] and ils[1] in stem_dictionaries[i]:
                important_words.append(Counter(stem_dictionaries[i][ils[1]]).most_common()[0][0])
        partition_words[list(partition_doc_dict.keys())[i]] = word_feature_filter(important_words, 3)
    return partitions_endpoints, partition_words


def db_info_network():
    db_info = [table for table in database_info() if len(get_services_from_table(table["name"])) > 0]
    # db_info = [table for table in database_info() if len(table['foreign_keys']) > 0]
    # db_info = [table for table in database_info()]
    documents = dict([(info['name'], document_from_database_info(info)) for info in db_info])
    similarity_matrix = similarity_network(documents.values())
    similarity_threshold = 0.2

    added_nodes = []
    wordnet_network = nx.Graph()
    vis_network = Network(height="100%", width="70%")
    node_name_map = {}
    for iy, y in enumerate(similarity_matrix):
        node_name_map[iy] = db_info[iy]['name']
        for ix, x in enumerate(y):
            y_name = db_info[iy]['name']
            x_name = db_info[ix]['name']
            sim = x
            if y_name in db_info[ix]['foreign_keys'] or x_name in db_info[iy]['foreign_keys']:
                sim += 0.5
            if sim > similarity_threshold and ix != iy:
                if y_name not in added_nodes:
                    vis_network.add_node(y_name)
                    # vis_network.add_node(y_name,
                    #                      color="#FF0000" if len(get_services_from_table(y_name)) > 0 else "#DDDDDD")
                    added_nodes.append(y_name)
                if x_name not in added_nodes:
                    vis_network.add_node(x_name)
                    # vis_network.add_node(x_name,
                    #                      color="#FF0000" if len(get_services_from_table(y_name)) > 0 else "#DDDDDD")
                    added_nodes.append(x_name)

                # norm_weight = (x - similarity_threshold) / (1 - similarity_threshold)
                vis_network.add_edge(y_name, x_name)
                wordnet_network.add_edge(y_name, x_name, weight=sim)

    all_partitioned_services, partitions_services, partition_words = partition_network(wordnet_network, documents,
                                                                                       node_name_map)
    all_services = [s['name'] for s in service_info(only_api_doc=True)]
    # put services with no partition in partition that has most services
    # overlapping with services git community neighbours
    git_partition, git_partitions_services = partition_git_network()
    for s in all_services:
        if s not in all_partitioned_services:
            neighbours = set(get_service_community_neighbours(s, git_partition, git_partitions_services))
            if len(neighbours) == 0:
                continue
            max_overlap = 0
            best_partition = 0
            for p, services in partitions_services.items():
                overlap = len(set(services).intersection(neighbours))
                if overlap > max_overlap:
                    max_overlap = overlap
                    best_partition = p
            partitions_services[best_partition].append(s)

    # nx.write_gexf(wordnet_network, "network_outputs/wordnet.gexf")
    # nx.draw_networkx(wordnet_network, node_size=3, with_labels=False)
    # plt.show()
    # vis_network.show_buttons(filter_=['physics'])
    # vis_network.show("network_outputs/wordnet.html")
    return partitions_services, partition_words

# api_docs_network()
# db_info_network()
