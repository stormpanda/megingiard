import re
import os

filepath = "/Users/maikthomalla/AndroidStudioProjects/Megingiard/app/src/main/java/com/stormpanda/megingiard/macropad/PadActionPicker.kt"

if not os.path.exists(filepath):
    print(f"Error: File {filepath} not found.")
    exit(1)

with open(filepath, 'r') as f:
    content = f.read()

# Add MaterialTheme import after Text import
content = content.replace(
    'import androidx.compose.material3.Text',
    'import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.Text',
    1
)

# Remove sp import (keep dp)
content = content.replace(
    'import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp',
    'import androidx.compose.ui.unit.dp'
)

# Replace fontSize = 14.sp with style = MaterialTheme.typography.bodyMedium
content = re.sub(r'fontSize = 14\.sp', 'style = MaterialTheme.typography.bodyMedium', content)

# Replace fontSize = 13.sp with style = MaterialTheme.typography.labelMedium
content = re.sub(r'fontSize = 13\.sp', 'style = MaterialTheme.typography.labelMedium', content)

# Replace fontSize = 12.sp with style = MaterialTheme.typography.bodySmall
content = re.sub(r'fontSize = 12\.sp', 'style = MaterialTheme.typography.bodySmall', content)

# Replace fontSize = 10.sp with style = MaterialTheme.typography.labelSmall
content = re.sub(r'fontSize = 10\.sp', 'style = MaterialTheme.typography.labelSmall', content)

with open(filepath, 'w') as f:
    f.write(content)

print("Done")

# Verify no fontSize = XX.sp remains
remaining = re.findall(r'fontSize\s*=\s*\d+\.sp', content)
print(f"Remaining fontSize usages: {remaining}")
