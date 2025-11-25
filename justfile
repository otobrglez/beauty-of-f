jvm_version := "25"
default_text := "SOS SOS SOS"
# voice := "Cellos"
voice := "Alex"


clean:
    rm -rf *.wav encoded.txt

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
