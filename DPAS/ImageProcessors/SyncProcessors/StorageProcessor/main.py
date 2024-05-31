from flask import Flask, request, Response
from ultralytics import YOLO
import argparse
import requests
import cv2
import numpy as np
import time
import json

model = {}
app = Flask(__name__)


def get_detections(image):
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
    image_file = request.files['image']
    image_data = image_file.read()
    image = cv2.imdecode(np.frombuffer(image_data, np.uint8), cv2.IMREAD_COLOR)
    image_name = f"received_image{time.time()}.jpg"
    detections = get_detections(image)

    files = {
        'file': (image_name, image_data, 'image/jpeg')
    }
    data = {
        'detections': json.dumps(detections)
    }

    response = requests.post(f'http://{output_address}/upload', files=files, data=data)

    if response.status_code == 201:
        print("Image with detections saved successfully")
    else:
        print(response.text)

    return Response(status=200)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Run the Flask app with input and output ports.')
    parser.add_argument('--input_port', type=int, required=True, help='Port for the Flask app to listen on')
    parser.add_argument('--output_port', type=int, required=True, help='Port for the output server')
    parser.add_argument('--model', type=str, required=True, help='Path to the model file')
    args = parser.parse_args()

    output_address = f'localhost:{args.output_port}'
    model = YOLO(args.model)
    app.run(host='0.0.0.0', port=args.input_port)
