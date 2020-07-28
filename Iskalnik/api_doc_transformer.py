import itertools
import math
import re
import nltk
from nltk.corpus import stopwords

nltk.download('wordnet')
stop_words = set(stopwords.words('english'))

path_weight = 1.
controller_weight = 0.6
summary_weight = 0.6
parameter_nesting_weight_k = 0.3
response_nesting_weight_k = 0.3

parameter_weight = 0.2
parameter_name_weight = 1.
parameter_model_name_weight = 1.
parameter_not_required_weight = 0.7

response_weight = 0.2
response_name_weight = 1.
response_model_name_weight = 1.

semantic_similarity_weight = 1.


def flatten(list_of_lists):
    return list(itertools.chain.from_iterable(list_of_lists))


def camel_case_split(text):
    matches = re.finditer('.+?(?:(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|$)', text)
    return [m.group(0) for m in matches]


def transform_endpoint_path(text, service_name):
    text = text.replace(service_name, ' ')
    text = ' '.join(camel_case_split(text)).lower()
    ignore_keywords = ["api", "v1", "v2", "{", "}", "-", "/", "."]
    for keyword in ignore_keywords:
        text = text.replace(keyword, ' ')
    return re.sub(r'\d+', '', text)


def transform_endpoint_controller(text, service_name):
    text = text.replace(service_name, ' ')
    text = ' '.join(camel_case_split(text)).lower()
    ignore_keywords = ["service", "-", "controller", "impl"]
    for keyword in ignore_keywords:
        text = text.replace(keyword, ' ')
    return re.sub(r'\d+', '', text)


def transform_endpoint_summary(text, service_name):
    text = text.replace(service_name, ' ')
    text = ' '.join(camel_case_split(text)).lower()
    ignore_keywords = ["com", "outbrain", "deprecated", "-", ",", "!", "(", ")", ".", "/", ";"]
    for keyword in ignore_keywords:
        text = text.replace(keyword, ' ')
    return re.sub(r'\d+', '', text)


def transform_parameter_response_name(text):
    text = ' '.join(camel_case_split(text)).lower()
    ignore_keywords = ["_", "-"]
    for keyword in ignore_keywords:
        text = text.replace(keyword, ' ')
    return re.sub(r'\d+', '', text)


def transform_parameter_response_model_name(text):
    text = ' '.join(camel_case_split(text)).lower()
    ignore_keywords = ["string", "integer", "collection", "»", "«", "response", "error", "seq", "set", "long"]
    for keyword in ignore_keywords:
        text = text.replace(keyword, ' ')
    return re.sub(r'\d+', '', text)


def tokenize_and_set_weight(text, weight):
    return [(w, weight) for w in nltk.word_tokenize(text) if w not in stop_words and len(w) > 1]


def transform_endpoint_to_document(endpoint):
    service_name = endpoint["service"]
    return tokenize_and_set_weight(transform_endpoint_path(endpoint["path"], service_name), path_weight) + \
           tokenize_and_set_weight(transform_endpoint_controller(endpoint["controller"], service_name),
                                   controller_weight) + \
           tokenize_and_set_weight(transform_endpoint_summary(endpoint["summary"], service_name), summary_weight)


def transform_parameter_response_to_document(parameter, weight, name_weight, model_name_weight, nesting_weight_k):
    if not parameter:
        return []
    required_weight = 1. if parameter["required"] else parameter_not_required_weight
    fields_document = flatten([transform_parameter_response_to_document
                               (field, weight * nesting_weight_k * required_weight, name_weight, model_name_weight,
                                nesting_weight_k)
                               for field in parameter["fields"]]) if parameter["fields"] else []
    return tokenize_and_set_weight(transform_parameter_response_name(parameter["name"]),
                                   weight * name_weight * required_weight) + \
           tokenize_and_set_weight(transform_parameter_response_model_name(parameter["modelName"] or ""),
                                   weight * model_name_weight * required_weight) + fields_document


def transform_api_to_document(endpoint, parameters, responses):
    document = transform_endpoint_to_document(endpoint)
    for p in parameters:
        document += transform_parameter_response_to_document(p, parameter_weight, parameter_name_weight,
                                                             parameter_model_name_weight, parameter_nesting_weight_k)
    for r in responses:
        document += transform_parameter_response_to_document(r, response_weight, response_name_weight,
                                                             response_model_name_weight, response_nesting_weight_k)
    return document


def add_importance_and_semantics(endpoints, documents, transformed_documents, service_info, endpoint_info):
    final_documents = []
    max_requests = math.log(max([e['requests'] for e in endpoint_info]) + 1, 10)
    for ind, endpoint in enumerate(endpoints):
        db_services = [s for s in service_info if s['name'] == endpoint['service']]
        if db_services:
            db_service = db_services[0]
        else:
            continue
        db_endpoint = [e['requests'] for e in endpoint_info if
                       e['service'] == endpoint['service'] and e['name'] in endpoint['summary']]
        requests = max(db_endpoint) if db_endpoint else 0.
        final_document = {
            'endpoint': endpoint,
            'document': documents[ind],
            'transformed_document': transformed_documents[ind],
            'consul_page_rank': float(db_service['consul_page_rank'] or 0.),
            'metrics_page_rank': float(db_service['metrics_page_rank'] or 0.),
            'metrics_weighted_page_rank': float(db_service['metrics_weighted_page_rank'] or 0.),
            'endpoint_requests': float(math.log(requests + 1, 10) / max_requests)
        }
        final_documents.append(final_document)
    return final_documents
