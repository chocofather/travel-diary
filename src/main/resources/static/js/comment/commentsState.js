// commentsState.js
export let loadedComments = [];

export function getLoadedComments() {
    return loadedComments;
}

export function setLoadedComments(arr) {
    loadedComments = arr;
}

export function addComment(c) {
    loadedComments.push(c);
}

export function concatComments(arr) {
    loadedComments = loadedComments.concat(arr);
}
