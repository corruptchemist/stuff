"""align_words.py AUDIO "known transcript" > words.json
Forced-aligns a known transcript to speech with pocketsphinx; prints [[word, start_s, end_s], ...]."""
import json, re, subprocess, sys
from pocketsphinx import Decoder

audio, text = sys.argv[1], sys.argv[2]
pcm = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", audio, "-ar", "16000", "-ac", "1", "-f", "s16le", "-"],
                     check=True, capture_output=True).stdout
words = re.findall(r"[A-Za-z']+", text.lower())
d = Decoder(samprate=16000, bestpath=False)
d.set_align_text(" ".join(words))
d.start_utt(); d.process_raw(pcm, full_utt=True); d.end_utt()
fps = 100  # pocketsphinx frames per second
out = [[re.sub(r"\(\d+\)$", "", s.word), s.start_frame / fps, (s.end_frame + 1) / fps]
       for s in d.seg() if s.word not in ("<s>", "</s>", "<sil>", "[NOISE]")]
json.dump(out, sys.stdout)
