(function () {
  "use strict";

  const form = document.querySelector("[data-cover-library-share-form]");
  if (!form) {
    return;
  }

  const includedChoices = Array.from(
      form.querySelectorAll(".diary-cover-library-included"));
  const rightsArea = form.querySelector("[data-rights-confirmation]");
  const rightsCheckbox = form.querySelector("[data-rights-checkbox]");
  const submitButton = form.querySelector("[data-share-submit]");

  function updateRightsRequirement() {
    const includesPhoto = includedChoices.some((choice) => choice.checked);
    if (rightsArea) {
      rightsArea.hidden = !includesPhoto;
    }
    if (rightsCheckbox) {
      rightsCheckbox.required = includesPhoto;
    }
  }

  form.addEventListener("change", (event) => {
    if (event.target.matches('input[type="radio"]')) {
      updateRightsRequirement();
    }
  });

  form.addEventListener("submit", () => {
    if (!submitButton) {
      return;
    }
    submitButton.disabled = true;
    submitButton.textContent = "공유 중…";
  });

  updateRightsRequirement();
})();
