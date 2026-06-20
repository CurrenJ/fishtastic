import shutil
import os

# Define source and destination directories
src_dir = "fabric/src/main/generated"
dst_dir = "neoforge/src/main/generated"

# Remove destination directory if it exists
if os.path.exists(dst_dir):
    shutil.rmtree(dst_dir)

# Copy the entire directory tree
shutil.copytree(src_dir, dst_dir)

print("Generated assets copied from Fabric to NeoForge successfully.")
