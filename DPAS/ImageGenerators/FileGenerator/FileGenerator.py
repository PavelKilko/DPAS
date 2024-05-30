import os
import requests

# Define the directory containing the images and the endpoint URL
images_directory = './images'
endpoint_url = 'http://localhost:8890`/process'

# Ensure the directory exists
if not os.path.exists(images_directory):
    print(f"Error: Directory {images_directory} does not exist.")
    exit()

processed_count = 0

for i in range(1, 37):
    # Process every 5th image
    if i == 1:
        image_path = os.path.join(images_directory, f'buttons.jpg')

        # Ensure the image file exists
        if not os.path.exists(image_path):
            print(f"Error: Image {image_path} does not exist.")
            continue

        # Read the image file
        with open(image_path, 'rb') as image_file:
            image_bytes = image_file.read()

        # Send the image to the endpoint
        response = requests.post(endpoint_url, files={'image': image_bytes})

        # Check the response
        if response.status_code == 200:
            print(f"Image {i}.jpg processed successfully.")
            processed_count += 1
        else:
            print(f"Error processing image {i}.jpg: {response.text}")

print(f"Image processing completed. Total images processed: {processed_count}")
