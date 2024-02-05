#!/usr/bin/env bash
set -e

for name in "${!FILE_@}"; do
    value="${!name}"
    if [[ $name == FILE_* ]]; then
        echo "Processing file $name"
        # Remove the FILE_ prefix and convert the variable name to lower case
        new_name=$(echo ${name#FILE_} | tr '[:upper:]' '[:lower:]')
        # Replace underscore with dot in the filename
        new_name=${new_name//_/.}
        # Decode the base64 value
        echo "$value" | base64 --decode > "$CONFIG_DIR/$new_name"
    fi
done

for name in "${!ZIP_@}"; do
    value="${!name}"
    if [[ $name == ZIP_* ]]; then
        echo "Processing file $name"
        # Remove the ZIP_ prefix and convert the variable name to lower case
        new_name=$(echo ${name#ZIP_} | tr '[:upper:]' '[:lower:]')
        # Replace underscore with dot in the filename
        new_name=${new_name//_/.}
        # Create a temporary directory for unzipping
        temp_dir=$(mktemp -d)
        # Decode the base64 value & save the decoded content to a file within the temporary directory
        echo "$value" | base64 -d > "$temp_dir/$new_name.zip"
        # Unzip the file
        unzip "$temp_dir/$new_name.zip" -d "$temp_dir"
        # Move the untarred content to the desired location
        mv "$temp_dir"/* "$CONFIG_DIR/"
        # Clean up the temporary directory
        rm -rf "$temp_dir"
    fi
done

# Execute the command that was passed as parameter to the script
exec "$@"