jvm_version := "25"
default_text := "SOS SOS SOS"

# voice := "Cellos"

voice := "Alex"

clean: clean-js
    rm -rf *.wav encoded.txt

# # Chapter 1 - Basics
encode input=default_text:
    scala-cli run src \
      --quiet \
      --main-class morseEncodeApp \
      --jvm={{ jvm_version }} -- "{{ input }}"

dib-say input=default_text:
    just encode "{{ input }}" | \
      sed 's/\./dit /g; s/-/dah /g'

cur-say input=default_text:
    just encode "{{ input }}" | \
      sed 's/\./\( /g; s/-/\) /g'

say input=default_text use_voice=voice:
    just dib-say "{{ input }}" | say -v {{ use_voice }}

# # Chapter 2 - Sound
encode-sound input=default_text file="output.wav":
    scala-cli run src \
        --quiet \
        --main-class morseEncodeToSoundApp \
        --jvm={{ jvm_version }} -- "{{ input }}" {{ file }}

encode-play input=default_text file="output-play.wav":
    rm -rf {{ file }} && \
        just encode-sound "{{ input }}" {{ file }} && \
        afplay {{ file }}

play-play input=default_text:
    scala-cli run src \
        --quiet \
        --main-class morseEncodeToSoundPlayApp \
        --jvm={{ jvm_version }} -- "{{ input }}"

## Chapter 3 - Decoding
clean-js:
    rm -rf dist

build-js-client: clean-js
    mkdir ./dist && \
      scala-cli --power package \
      --js src src-client \
      --main-class=MorseClient \
      -f \
      --jvm={{ jvm_version }} \
      -o ./dist/morse-client.js

copy-to-dist:
    cp src-client/* ./dist/

build-js: build-js-client copy-to-dist

serve-js:
    yarn run http-server -c-1 ./dist

deploy: build-js
    yarn run netlify deploy --prod