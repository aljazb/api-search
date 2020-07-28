from flask import Flask, jsonify, request

from evaluator import evaluate
from group_search_evaluator import evaluate_grouping, init_cache
from wordnet_similarity import calculate_similarity, init
import api_doc_transformer

app = Flask(__name__)


@app.route('/', methods=['GET'])
def get():
    return jsonify({'msg': 'Hello World'})


@app.route('/search', methods=['POST'])
def search():
    query = request.json['query']
    calculate_similarity(request.json['query'], request.json['consul'], request.json['metrics'], request.json['weighted_metrics'], request.json['endpoint_requests'])
    return jsonify({'msg': 'OK'})


@app.route('/evaluate', methods=['POST'])
def evaluate_queries():
    evaluate(5, request.json['consul'], request.json['metrics'], request.json['weighted_metrics'], request.json['endpoint_requests'])
    return jsonify({'msg': 'OK'})


cached_filters = []
partitions_services = []
partition_words = []


@app.route('/init-grouping', methods=['POST'])
def init_grouping():
    global cached_filters
    global partitions_services
    global partition_words
    partitions_services, partition_words, cached_filters = init_cache()
    return jsonify({'msg': 'OK'})


@app.route('/evaluate-grouping', methods=['POST'])
def evaluate_grouping_endpoint():
    evaluate_grouping(request.json['k'], request.json['consul'], request.json['metrics'], request.json['weighted_metrics'], request.json['endpoint_requests'])
    return jsonify({'msg': 'OK'})


@app.route('/set-weights', methods=['POST'])
def set_weights():
    api_doc_transformer.path_weight = request.json['path_weight']
    api_doc_transformer.controller_weight = request.json['controller_weight']
    api_doc_transformer.summary_weight = request.json['summary_weight']
    api_doc_transformer.parameter_nesting_weight_k = request.json['parameter_nesting_weight_k']
    api_doc_transformer.response_nesting_weight_k = request.json['response_nesting_weight_k']

    api_doc_transformer.parameter_weight = request.json['parameter_weight']
    init()
    return jsonify({'msg': 'OK'})


if __name__ == '__main__':
    app.run(debug=True)
