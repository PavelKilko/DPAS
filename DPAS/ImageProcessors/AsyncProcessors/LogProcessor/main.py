from flask import Flask, request, Response
from ultralytics import YOLO
from celery_utils import get_celery_app_instance
from datetime import datetime
import argparse
import requests
import cv2
import numpy as np
import time
import json
import os
import time

name = os.path.splitext(os.path.basename(__file__))[0]
app = Flask(name)
celery = get_celery_app_instance(app)


def get_detections(image, model):
    predictions = model(image, conf=0.3)[0].cpu().numpy()

    names = predictions.names

    detections = []

    for prediction in predictions:
        for box in prediction.boxes:
            x_min = box.xyxy[0][0].item()
            y_min = box.xyxy[0][1].item()
            x_max = box.xyxy[0][2].item()
            y_max = box.xyxy[0][3].item()
            confidence = box.conf[0].item()
            output_label = names[int(box.cls[0].item())]

            detections.append(
                {
                    "tag_name": output_label,
                    "confidence": confidence,
                    "x_min": x_min,
                    "y_min": y_min,
                    "x_max": x_max,
                    "y_max": y_max
                }
            )

    return detections


@app.route('/process', methods=['POST'])
def process():
    if 'image' not in request.files:
        return Response(400)

    process_and_log.delay(request.files['image'].read(), args.model)

    return Response(status=200)


@celery.task(bind=True)
def process_and_log(self, image_data, model_path):
    image = cv2.imdecode(np.frombuffer(image_data, np.uint8), cv2.IMREAD_COLOR)
    model = YOLO(model_path)
    detections = get_detections(image, model)
    name = f"received_image-{datetime.now().strftime('%Y%m%d%H%M%S')}-{self.request.id}"
    json_name = f"{name}.json"
    directory = "logs"

    if not os.path.exists(directory):
        os.mkdir(directory)

    json_path = os.path.join(directory, json_name)
    with open(json_path, "w") as f:
        f.write(json.dumps(detections, indent=4))

    # files = {
    #     'file': (image_name, image_data, 'image/jpeg')
    # }
    # data = {
    #     'detections': json.dumps(detections)
    # }
    #
    # response = requests.post(f'http://{output_address}/upload', files=files, data=data)
    #
    # if response.status_code == 201:
    #     print("Image with detections saved successfully")
    # else:
    #     print(response.text)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Run the Flask app with input and output ports.')
    parser.add_argument('--input_port', type=int, required=True, help='Port for the Flask app to listen on')
    parser.add_argument('--model', type=str, required=True, help='Path to the model file')
    args = parser.parse_args()

    output_address = f'localhost:{args.output_port}'
    app.run(host='0.0.0.0', port=args.input_port)
