#!/usr/bin/env bash

docker_image_ref() {
  local repo="$1"
  local image_tag="$2"
  printf '%s/%s:%s' "${REGISTRY_HOST_DOCKER}" "${repo}" "${image_tag}"
}

cluster_image_ref() {
  local repo="$1"
  local image_tag="$2"
  printf '%s/%s:%s' "${REGISTRY_HOST_CLUSTER}" "${repo}" "${image_tag}"
}

build_and_push_image() {
  local module="$1"
  local repo="$2"
  local image_tag="$3"
  local image

  image="$(docker_image_ref "${repo}" "${image_tag}")"
  log "Building image ${image}"
  docker build --build-arg APP_MODULE="${module}" -f "${SAMPLES_DIR}/Dockerfile" -t "${image}" "${ROOT_DIR}"
  log "Pushing image ${image}"
  docker push "${image}"
}
