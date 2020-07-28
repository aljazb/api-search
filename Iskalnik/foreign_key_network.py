import networkx as nx
import matplotlib.pyplot as plt
from pyvis.network import Network
from itertools import chain

from colors import get_different_colors
from database import database_info, service_info
from elasticsearch_client import get_services_from_table
from string_utils import document_from_database_info
from tfidf_kmeans import tfidf_kmeans
from wordnet_network import get_wordnet_labels

foreign_key_graph = nx.Graph()

db_info = database_info()
connected_nodes = set(chain.from_iterable(
    [table['foreign_keys'] + [table['name']] for table in db_info if len(table['foreign_keys']) > 0]))
db_info = [info for info in db_info if info['name'] in connected_nodes]

documents = [document_from_database_info(info) for info in db_info]
k = 10
labels = tfidf_kmeans(documents, k)
# labels = get_wordnet_labels(documents)
vis_network = Network(height="100%", width="70%")

colors = get_different_colors(max(labels)+1)
# labels from k means
color_labels = [colors[l] for l in labels]
# services use this tables
# color_labels = ["#FF0000" if len(get_services_from_table(x['name'])) > 0 else "#DDDDDD" for x in db_info]

vis_network.add_nodes([x['name'] for x in db_info], color=color_labels)

for table in db_info:
    for key in table['foreign_keys']:
        foreign_key_graph.add_edge(table['name'], key)
        if key in vis_network.get_nodes():
            vis_network.add_edge(table['name'], key)

# git_network = Network(height="750px", width="100%")
# git_network.from_nx(foreign_key_graph)

# git_network.show_buttons(filter_=['physics'])
# git_network.show("network_outputs/foreign_key.html")

vis_network.show_buttons(filter_=['physics'])
vis_network.show("network_outputs/foreign_key.html")
