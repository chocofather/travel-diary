(function () {
  "use strict";

  const form = document.querySelector("[data-cover-library-download-form]");
  if (!form) {
    return;
  }

  form.addEventListener("submit", () => {
    const submitButton = form.querySelector("[data-download-submit]");
    if (!submitButton) {
      return;
    }
    submitButton.disabled = true;
    submitButton.textContent = "추가 중…";
  });
})();
