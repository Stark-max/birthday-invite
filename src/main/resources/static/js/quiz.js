function startQuiz(instanceId, guestId) {
  const configEl = document.getElementById(`activity-config-${instanceId}`);
  const root = document.getElementById(`quiz-root-${instanceId}`);
  if (!configEl || !root) return;
  const config = JSON.parse(configEl.textContent);
  const questions = config.questions || [];
  const answers = {};
  let index = 0;

  const render = () => {
    const question = questions[index];
    const percent = questions.length ? Math.round((index / questions.length) * 100) : 0;
    root.innerHTML = `
      <div class="quiz-progress"><span style="width:${percent}%"></span></div>
      <h3 style="margin-top:14px">${question.text}</h3>
      <div id="quiz-options-${instanceId}"></div>
    `;
    const options = root.querySelector(`#quiz-options-${instanceId}`);
    if (question.type === "multiple_choice") {
      (question.options || []).forEach((option, optionIndex) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "secondary quiz-option";
        btn.textContent = option;
        btn.onclick = () => {
          answers[question.id] = optionIndex;
          next();
        };
        options.appendChild(btn);
      });
    } else {
      options.innerHTML = `
        <input id="quiz-text-${instanceId}" placeholder="Ответ" />
        <button type="button" class="quiz-option" onclick="window.__quizNext${instanceId}()">Ответить</button>
      `;
      window[`__quizNext${instanceId}`] = () => {
        answers[question.id] = document.getElementById(`quiz-text-${instanceId}`).value;
        next();
      };
    }
  };

  const next = () => {
    index++;
    if (index < questions.length) {
      render();
      return;
    }
    fetch(`/activities/${instanceId}/play`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ guestId, answers })
    })
      .then((r) => r.json())
      .then((result) => {
        root.innerHTML = `<div class="card"><h3>${result.success ? "Готово" : "Не получилось"}</h3><p>${result.message}</p><p><strong>${result.points || 0}</strong> очков</p></div>`;
      });
  };

  if (!questions.length) {
    root.innerHTML = "<p>Викторина пока не настроена.</p>";
    return;
  }
  render();
}
