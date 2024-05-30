import cv2
import requests

# Define the video file path and the endpoint URL
video_file_path = './videos/video-2.mp4'
endpoint_url = 'http://localhost:8889/process'

# Open the video file
cap = cv2.VideoCapture(video_file_path)

# Check if video opened successfully
if not cap.isOpened():
    print("Error: Could not open video file.")
    exit()

frame_count = 0
processed_count = 0

while True:
    # Read a frame from the video
    ret, frame = cap.read()

    # If no frame is read (end of video), break the loop
    if not ret:
        break

    # Process every 300th frame
    if frame_count % 300 == 0:
        # Encode the frame as JPEG
        ret, buffer = cv2.imencode('.jpg', frame)
        if not ret:
            print(f"Error: Could not encode frame {frame_count}.")
            frame_count += 1
            continue

        # Convert the buffer to bytes
        frame_bytes = buffer.tobytes()

        # Send the frame to the endpoint
        response = requests.post(endpoint_url, files={'image': frame_bytes})

        # Check the response
        if response.status_code == 200:
            print(f"Frame {frame_count} processed successfully.")
            processed_count += 1
        else:
            print(f"Error processing frame {frame_count}: {response.text}")

    frame_count += 1

# Release the video capture object
cap.release()
print(f"Video processing completed. Total frames processed: {processed_count}")
