function initCountdownTimer(root) {
  const instanceId = root.dataset.instanceId;
  const eventDate = root.dataset.eventDate;
  const timer = document.getElementById(`countdown-timer-${instanceId}`);
  if (!timer || !eventDate) return;
  const target = new Date(`${eventDate}T00:00:00`);
  const render = () => {
    const diff = Math.max(0, target.getTime() - Date.now());
    const days = Math.floor(diff / 86400000);
    const hours = Math.floor((diff % 86400000) / 3600000);
    const minutes = Math.floor((diff % 3600000) / 60000);
    timer.textContent = `${days} дн. ${hours} ч. ${minutes} мин.`;
  };
  render();
  setInterval(render, 60000);
}

function initCountdownTasks(root) {
  const instanceId = root.dataset.instanceId;
  const results = readCountdownJson(`countdown-results-${instanceId}`);
  const state = { completedByTaskId: new Map() };
  results.forEach((result) => {
    const data = result.data || {};
    if (data.type === "countdown-challenge" && data.taskId) {
      state.completedByTaskId.set(String(data.taskId), result);
    }
  });
  root.__countdownState = state;
  root.querySelectorAll(".countdown-task-card[data-task-id]").forEach((card) => applyCountdownTaskState(root, card));
}

function applyCountdownTaskState(root, card) {
  const instanceId = root.dataset.instanceId;
  const taskId = card.dataset.taskId;
  const completed = root.__countdownState?.completedByTaskId.get(String(taskId));
  const state = completed ? "completed" : countdownTaskAvailability(card);
  const input = document.getElementById(`countdown-answer-${instanceId}-${taskId}`);
  const button = card.querySelector("[data-countdown-complete]");
  const stateText = document.getElementById(`countdown-state-${instanceId}-${taskId}`);
  const badge = card.querySelector(".task-state-badge");

  card.classList.remove("is-locked", "is-available", "is-completed", "is-missed", "completed");
  card.classList.add(`is-${state}`);
  if (input) input.disabled = state !== "available";
  if (button) button.disabled = state !== "available";

  if (state === "completed") {
    const answer = completed?.data?.answer;
    if (input && input.type !== "checkbox" && answer !== undefined) input.value = answer;
    if (input && input.type === "checkbox") input.checked = answer === true || answer === "true";
    if (badge) badge.textContent = "Выполнено";
    if (stateText) stateText.textContent = answer && answer !== true ? `Ответ: ${answer}` : "Задание выполнено.";
    return;
  }

  const unlockDate = countdownUnlockDate(card);
  if (state === "locked") {
    if (badge) badge.textContent = "Закрыто";
    if (stateText) stateText.textContent = `Откроется ${formatRuDate(unlockDate)}.`;
  } else if (state === "missed") {
    if (badge) badge.textContent = "Пропущено";
    if (stateText) stateText.textContent = "Это задание уже нельзя выполнить.";
  } else {
    if (badge) badge.textContent = "Доступно";
    if (stateText) stateText.textContent = "Можно выполнить сейчас.";
  }
}

function countdownTaskAvailability(card) {
  if (card.dataset.unlockMode === "all_at_once") return "available";
  const today = localDateOnly(new Date());
  const unlockDate = countdownUnlockDate(card);
  if (today < unlockDate) return "locked";
  const allowLate = card.dataset.allowLateCompletion === "true";
  const currentOnly = card.dataset.missedDaysPolicy === "current_only";
  if ((!allowLate || currentOnly) && today > unlockDate) return "missed";
  return "available";
}

function countdownUnlockDate(card) {
  const eventDate = parseLocalDate(card.dataset.eventDate);
  const dayOffset = Number(card.dataset.dayOffset || 0);
  const unlockDate = new Date(eventDate);
  unlockDate.setDate(unlockDate.getDate() - dayOffset);
  return localDateOnly(unlockDate);
}

function completeCountdownTask(buttonOrInstanceId, maybeGuestId, maybeTaskId) {
  const button = typeof buttonOrInstanceId === "object" ? buttonOrInstanceId : null;
  const instanceId = button ? button.dataset.instanceId : buttonOrInstanceId;
  const guestId = button ? button.dataset.guestId : maybeGuestId;
  const taskId = button ? button.dataset.taskId : maybeTaskId;
  const card = document.getElementById(`countdown-task-${instanceId}-${taskId}`);
  const root = document.getElementById(`countdown-root-${instanceId}`);
  const output = document.getElementById(`countdown-result-${instanceId}`);
  if (!card || !root) return;
  const type = card.dataset.taskType || "text";
  const input = document.getElementById(`countdown-answer-${instanceId}-${taskId}`);
  const payload = {
    guestId,
    type: "complete_task",
    taskId
  };
  if (type === "checkbox") {
    payload.completed = Boolean(input?.checked);
  } else {
    payload.answer = input?.value || "";
  }
  fetch(`/activities/${instanceId}/play`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  })
    .then((response) => response.json())
    .then((result) => {
      if (output) output.textContent = result.message;
      if (result.success) {
        root.__countdownState = root.__countdownState || { completedByTaskId: new Map() };
        root.__countdownState.completedByTaskId.set(String(taskId), {
          id: result.data?.resultId,
          points: result.points || 0,
          data: result.data || {}
        });
        applyCountdownTaskState(root, card);
      }
    })
    .catch(() => {
      if (output) output.textContent = "Не удалось выполнить задание.";
    });
}

function readCountdownJson(id) {
  const el = document.getElementById(id);
  if (!el) return [];
  try {
    const parsed = JSON.parse(el.textContent);
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function parseLocalDate(value) {
  const [year, month, day] = String(value || "").split("-").map(Number);
  return localDateOnly(new Date(year, (month || 1) - 1, day || 1));
}

function localDateOnly(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

function formatRuDate(timestamp) {
  const date = new Date(timestamp);
  return date.toLocaleDateString("ru-RU", { day: "2-digit", month: "2-digit", year: "numeric" });
}

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".countdown-challenge[data-instance-id]").forEach((root) => {
    initCountdownTimer(root);
    initCountdownTasks(root);
  });
});
