function copyInviteLink(code) {
  const url = `${window.location.origin}/invite/${code}`;
  navigator.clipboard.writeText(url).then(() => {
    const el = document.querySelector(`[data-copy-code="${code}"]`);
    if (!el) return;
    const original = el.textContent;
    el.textContent = "Скопировано";
    setTimeout(() => {
      el.textContent = original;
    }, 2000);
  });
}

function toggleDeclineForm() {
  document.getElementById("decline-form")?.classList.toggle("hidden");
}

function addWishlistField(containerId = "wishlist-fields") {
  const container = document.getElementById(containerId);
  const template = document.getElementById("wishlist-field-template");
  if (!container || !template) return;
  container.appendChild(template.content.cloneNode(true));
}

function removeWishlistField(button) {
  button.closest(".wishlist-editor-card, .wishlist-field")?.remove();
}

function addActivityRow(templateId, containerId) {
  const template = document.getElementById(templateId);
  const container = document.getElementById(containerId);
  if (!template || !container) return;
  container.appendChild(template.content.cloneNode(true));
}

function removeActivityRow(button) {
  button.closest(".activity-editor-row")?.remove();
}

function confirmDelete(guestName) {
  return confirm(`Удалить гостя "${guestName}"?`);
}

function toggleActivity(id) {
  document.getElementById(`activity-${id}`)?.classList.toggle("hidden");
}

function toggleWishlistItem(button, itemId, guestId) {
  if (!button) return;
  const card = document.getElementById(`wishlist-item-${itemId}`);
  const status = document.getElementById(`wishlist-status-${itemId}`);
  const originalText = button.textContent;
  button.disabled = true;
  button.textContent = "...";

  fetch(`/wishlist/${itemId}/toggle`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ guestId })
  })
    .then((response) => response.json())
    .then((result) => {
      if (!result.success) {
        if (status) status.textContent = result.message;
        button.textContent = originalText;
        return;
      }

      const isMine = Number(result.reservedByGuestId) === Number(guestId);
      card?.classList.toggle("available", !result.reserved);
      card?.classList.toggle("reserved-by-me", isMine);
      card?.classList.toggle("reserved", result.reserved && !isMine);
      if (status) status.textContent = result.reserved ? "Бронь: вы" : "Свободно";
      button.classList.toggle("selected", isMine);
      button.textContent = isMine ? "Отменить бронь" : "Забронировать";
      button.setAttribute("aria-label", isMine ? "Отменить бронь" : "Забронировать подарок");
    })
    .catch(() => {
      if (status) status.textContent = "Не получилось обновить подарок.";
      button.textContent = originalText;
    })
    .finally(() => {
      button.disabled = false;
    });
}
