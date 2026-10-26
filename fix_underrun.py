import re

with open("app/src/main/java/com/example/TtsManager.kt", "r") as f:
    content = f.read()

old_write = """                        // Write blocks until buffer has space
                        track.write(audioBytes, 0, audioBytes.size)"""

new_write = """                        // Write blocks until buffer has space
                        track.write(audioBytes, 0, audioBytes.size)
                        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            track.play()
                        }"""

if old_write in content:
    content = content.replace(old_write, new_write)
    with open("app/src/main/java/com/example/TtsManager.kt", "w") as f:
        f.write(content)
    print("Fixed AudioTrack underrun!")
else:
    print("Could not find write block")
