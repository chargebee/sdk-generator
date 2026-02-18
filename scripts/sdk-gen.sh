#!/usr/bin/env bash
set -euo pipefail

# Checkout or update the required sdk repos: sdk-gen.sh setup <lang1> <lang2>
# Generate the SDKs: sdk-gen.sh generate <lang1> <lang2>
BASE_DIR="${BASE_DIR:-"../chargebee-sdks"}"
LANGS=${LANGS:-"java node python php go ruby dotnet"}

function _gradlew() {
    args=("-i ${BASE_DIR}/chargebee_sdk_spec.json")
    case "$1" in
        java) args+=("-l JAVA_V4 -o $BASE_DIR/chargebee-java/src/main/java/");;
        php) args+=("-l PHP_V4 -o $BASE_DIR/chargebee-php/src");;
        python) args+=("-l PYTHON_V3 -o $BASE_DIR/chargebee-python/chargebee");;
        node) args+=("-l NODE_V3 -o $BASE_DIR/chargebee-node/src/resources");;
        ruby) args+=("-l RUBY -o $BASE_DIR/chargebee-ruby/lib/chargebee");;
        dotnet) args+=("-l DOTNET -o $BASE_DIR/chargebee-dotnet/ChargeBee");;
        go) args+=("-l GO -o $BASE_DIR/chargebee-go");;
        *) echo "Unknown language"; exit 1;;
    esac

    ./gradlew run --args="${args[*]}" ${DEBUG:+"--debug-jvm"}
}

function _format() {
    local lang="$1"
    local dir="$2"
    case "$lang" in
        go) pushd "$dir"
            goimports-reviser -rm-unused -use-cache -format -apply-to-generated-files ./...
            popd;;
        *) echo "Formatter not available for $lang";;
    esac
}

function setup() {
    # Checkout the required repos
    local langs="${@:-"$LANGS"}"
    for repo in $langs; do
        dir="${BASE_DIR}/chargebee-${repo}"
        # if the directory exists, pull the latest changes
        if [ -d "$dir" ]; then
            pushd "$dir"
            git pull
            popd
        else
            git clone https://github.com/chargebee/chargebee-${repo}.git "$dir"
        fi
    done

    # Update openapi spec
    wget https://github.com/chargebee/openapi/raw/refs/heads/main/spec/chargebee_sdk_spec.json -O "${BASE_DIR}/chargebee_sdk_spec.json"
}

function generate() {
    local sdk_langs="${@:?"No language specified, pass a list of languages to generate the SDK"}"
    for lang in $sdk_langs; do
        _gradlew $lang
    done

    for lang in $sdk_langs; do
        _format $lang "${BASE_DIR}/chargebee-${lang}"
    done
}

if [ $# -eq 0 ]; then
    echo "Chargebee SDK generator"
    echo "Usage: sdk-gen.sh setup|generate <lang1> <lang2>"
else
    $@
fi
