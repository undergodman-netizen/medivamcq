import re

with open("app/src/main/java/com/example/TtsManager.kt", "r") as f:
    content = f.read()

content = content.replace(
    'if (!response.isSuccessful) return null',
    'if (!response.isSuccessful) { android.util.Log.e("TtsManager", "API Error: ${response.code} ${response.body?.string()}"); return null }'
)

content = content.replace(
    'return outStream.toByteArray()',
    'val bytes = outStream.toByteArray(); android.util.Log.d("TtsManager", "Fetched chunk size: ${bytes.size}"); return bytes'
)

with open("app/src/main/java/com/example/TtsManager.kt", "w") as f:
    f.write(content)
