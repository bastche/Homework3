import os
import json
import chardet

PATH = "C:\\Users\\brasi\\Documents\\Télécom Sud Paris\\International\\Cours\\Ingénieur des données\\Homework 3\\all_useful_tables"

def detect_encoding(file_path):
    with open(file_path, 'rb') as f:
        result = chardet.detect(f.read())
    return result['encoding']

def validate_json(file_path, encoding):
    try:
        with open(file_path, 'r', encoding=encoding) as file:
            data = json.load(file)
        # The JSON file is valid.
        return 0
    except json.JSONDecodeError as e:
        # Error in the JSON file.
        return 1
    except FileNotFoundError:
        print(f"File {file_path} not found.")

def repair_file(file_path, encoding):
    with open(file_path, "r", encoding=encoding) as f:
        # Read the file content and store it in a variable.
        json_data = f.read()
        length = len(json_data) - 1
        i = 0
        while i < length:
            if json_data[i] == "}" and json_data[i + 1] == "{":
                new_json_data = json_data[:i] + "," + json_data[i + 2:]
                json_data = new_json_data
                length -= 1
            i += 1

    with open(file_path, "w", encoding=encoding) as f:
        f.write(new_json_data)

def is_json_content_empty(file_path, encoding="utf-8"):
    with open(file_path, "r", encoding=encoding) as f:
        content = f.read().strip()
    return content in ["", "{}", "[]"]

def json_filter():
    empty_files_count = 0
    global_footnotes_count = 0
    for file_name in os.listdir(PATH):
        file_path = os.path.join(PATH, file_name)
        encoding = "utf-8"
        
        # Remove empty JSON files.
        if is_json_content_empty(file_path):
            os.remove(file_path)
            empty_files_count += 1
        
        # Repair invalid JSON files.
        if validate_json(file_path, encoding) == 1:
            print(empty_files_count)
            empty_files_count += 1
            print(file_name)
            repair_file(file_path, encoding)
        
        # Remove files with no tables.
        if read_number_of_tables(file_path) == 0:
            os.remove(file_path)
        
        # Count files with global footnotes.
        if read_global_footnotes(file_path) is not None:
            global_footnotes_count += 1
            print(file_name)
    
    print(global_footnotes_count)

def read_number_of_tables(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)  # Convert the JSON content into a Python dictionary or list.
    return data.get("PAPER'S NUMBER OF TABLES")

def read_global_footnotes(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)  # Convert the JSON content into a Python dictionary or list.
    return data.get("global_footnotes")


json_filter()
