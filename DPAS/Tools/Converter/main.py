import json
import yaml
import argparse
import os
import shutil
import random


def convert_json_to_yaml(json_file_path, yaml_file_path):
    with open(json_file_path, 'r') as json_file:
        data = json.load(json_file)

    max_id = max(item['id'] for item in data) if data else -1

    yaml_data = {'names': {}}
    for item in data:
        yaml_data['names'][item['id'] - 1] = item['name']

    for id in range(max_id):
        if id not in yaml_data['names']:
            yaml_data['names'][id] = 'undefined'

    full_yaml_data = {
        'path': '',
        'train': '',
        'val': '',
        'test': '',
        'names': yaml_data['names']
    }

    with open(yaml_file_path, 'w') as yaml_file:
        yaml.dump(full_yaml_data, yaml_file, default_flow_style=False)

    print("YAML file has been created.")


def create_directory_structure(main_directory, output_directory):
    detections_src = os.path.join(main_directory, 'detections')
    images_src = os.path.join(main_directory, 'images')
    labels_dest = os.path.join(output_directory, 'labels')
    images_dest = os.path.join(output_directory, 'images')

    os.makedirs(output_directory, exist_ok=True)

    os.makedirs(labels_dest, exist_ok=True)
    os.makedirs(images_dest, exist_ok=True)

    for split in ['train', 'val', 'test']:
        os.makedirs(os.path.join(labels_dest, split), exist_ok=True)
        os.makedirs(os.path.join(images_dest, split), exist_ok=True)

    print(f"Directories '{labels_dest}' and '{images_dest}' have been created.")


def convert_to_yolo_format(item):
    x_center = (item['x_min'] + item['x_max']) / 2.0 / item['width']
    y_center = (item['y_min'] + item['y_max']) / 2.0 / item['height']
    width = (item['x_max'] - item['x_min']) / item['width']
    height = (item['y_max'] - item['y_min']) / item['height']
    return f"{item['tag_id']} {x_center:.6f} {y_center:.6f} {width:.6f} {height:.6f}\n"


def convert_and_split_detections_json_to_txt(detections_src, labels_dest, train_files, val_files):
    for json_filename in os.listdir(detections_src):
        if json_filename.endswith('.json'):
            json_file_path = os.path.join(detections_src, json_filename)
            base_filename = json_filename.replace('.json', '')
            txt_filename = f"{base_filename}.txt"

            if base_filename in train_files:
                split = 'train'
            else:
                split = 'val'

            txt_file_path = os.path.join(labels_dest, split, txt_filename)

            with open(json_file_path, 'r') as json_file:
                data = json.load(json_file)

            with open(txt_file_path, 'w') as txt_file:
                for item in data:
                    line = convert_to_yolo_format({
                        'id': item['id'],
                        'tag_id': item['tag_id'] - 1,
                        'x_min': item['x_min'],
                        'y_min': item['y_min'],
                        'x_max': item['x_max'],
                        'y_max': item['y_max'],
                        'width': item['width'],
                        'height': item['height']
                    })
                    txt_file.write(line)

            print(f"Converted {json_file_path} to {txt_file_path} in YOLO format")


def split_dataset(images_src, labels_src, images_dest, labels_dest, train_ratio=0.8):
    image_files = [f for f in os.listdir(images_src) if f.endswith('.jpg')]
    random.shuffle(image_files)

    split_index = int(train_ratio * len(image_files))

    train_files = image_files[:split_index]
    val_files = image_files[split_index:]

    for file_list, split in [(train_files, 'train'), (val_files, 'val')]:
        for image_file in file_list:
            shutil.copy(os.path.join(images_src, image_file), os.path.join(images_dest, split, image_file))

    print(f"Split dataset into {len(train_files)} train and {len(val_files)} val images.")
    return [f.replace('.jpg', '') for f in train_files], [f.replace('.jpg', '') for f in val_files]


def duplicate_val_to_test(images_dest, labels_dest):
    for split in ['images', 'labels']:
        val_dir = os.path.join(images_dest if split == 'images' else labels_dest, 'val')
        test_dir = os.path.join(images_dest if split == 'images' else labels_dest, 'test')

        for filename in os.listdir(val_dir):
            shutil.copy(os.path.join(val_dir, filename), os.path.join(test_dir, filename))

    print("Duplicated 'val' directory to 'test' directory.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Convert JSON file to YAML file and create directory structure.')
    parser.add_argument('main_directory', type=str, help='Path to the main directory')
    parser.add_argument('output_directory', type=str, help='Path to the output directory')

    args = parser.parse_args()

    main_directory = args.main_directory
    output_directory = args.output_directory

    json_file_path = os.path.join(main_directory, 'tags.json')
    yaml_file_path = os.path.join(output_directory, 'tags.yaml')

    create_directory_structure(main_directory, output_directory)

    convert_json_to_yaml(json_file_path, yaml_file_path)

    images_src = os.path.join(main_directory, 'images')
    labels_src = os.path.join(main_directory, 'detections')
    images_dest = os.path.join(output_directory, 'images')
    labels_dest = os.path.join(output_directory, 'labels')
    train_files, val_files = split_dataset(images_src, labels_src, images_dest, labels_dest)

    convert_and_split_detections_json_to_txt(labels_src, labels_dest, train_files, val_files)

    duplicate_val_to_test(images_dest, labels_dest)
