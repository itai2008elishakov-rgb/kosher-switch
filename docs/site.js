// Shared motion for every page: nav, reveal on scroll, progress bar, tilt, parallax,
// magnetic buttons and the iPhone scroll story. Everything is skipped for reduced motion.
(() => {
  const still = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const fine = matchMedia("(pointer: fine)").matches;

  // Nav gets a glass background once the page scrolls.
  const nav = document.getElementById("nav");
  const onScroll = () => nav && nav.classList.toggle("scrolled", scrollY > 20);
  addEventListener("scroll", onScroll, { passive: true }); onScroll();

  // Reveal on scroll (the pages' own copy of this is harmless: "in" is just added once).
  const io = new IntersectionObserver(es => es.forEach(e => {
    if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
  }), { threshold: 0.15 });
  document.querySelectorAll(".reveal").forEach(el => io.observe(el));

  // A thin gold progress line under the nav.
  const bar = document.createElement("div");
  bar.className = "progress";
  document.body.appendChild(bar);
  const progress = () => {
    const max = document.documentElement.scrollHeight - innerHeight;
    bar.style.transform = `scaleX(${max > 0 ? scrollY / max : 0})`;
  };
  addEventListener("scroll", progress, { passive: true }); progress();

  // The Android hero switch: tap to turn kosher mode on and off yourself.
  document.querySelectorAll("button.toggle").forEach(t => {
    const phone = t.closest(".phone");
    t.addEventListener("click", () => {
      const on = !phone.classList.contains("on");
      phone.classList.add("manual");
      phone.classList.toggle("on", on);
      t.setAttribute("aria-pressed", on);
      if (navigator.vibrate) navigator.vibrate(12);
    });
  });

  if (still) {
    document.querySelectorAll(".story").forEach(s => s.classList.add("static"));
    return;
  }

  // Phones and cards lean toward the cursor.
  if (fine) document.querySelectorAll("[data-tilt]").forEach(el => {
    const max = parseFloat(el.dataset.tilt) || 10;
    el.addEventListener("pointermove", e => {
      const r = el.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
      el.style.transform = `perspective(900px) rotateY(${x * max}deg) rotateX(${-y * max}deg)`;
      el.style.setProperty("--gx", `${(x + 0.5) * 100}%`);
      el.style.setProperty("--gy", `${(y + 0.5) * 100}%`);
    });
    el.addEventListener("pointerleave", () => { el.style.transform = ""; });
  });

  // Buttons are pulled slightly toward the cursor.
  if (fine) document.querySelectorAll(".btn").forEach(b => {
    b.addEventListener("pointermove", e => {
      const r = b.getBoundingClientRect();
      b.style.transform = `translate(${(e.clientX - r.left - r.width / 2) * 0.15}px, ${(e.clientY - r.top - r.height / 2) * 0.25 - 2}px)`;
    });
    b.addEventListener("pointerleave", () => { b.style.transform = ""; });
  });

  // Parallax: elements with data-speed drift as the page scrolls.
  const para = [...document.querySelectorAll("[data-speed]")];
  if (para.length) {
    const move = () => para.forEach(el => {
      const r = el.getBoundingClientRect();
      const mid = r.top + r.height / 2 - innerHeight / 2;
      el.style.translate = `0 ${mid * -parseFloat(el.dataset.speed)}px`;
    });
    addEventListener("scroll", () => requestAnimationFrame(move), { passive: true }); move();
  }

  // iPhone scroll story: the sticky phone shows the screen of the step in the middle of the view.
  document.querySelectorAll(".story").forEach(story => {
    const steps = [...story.querySelectorAll(".story-step")];
    const shots = [...story.querySelectorAll(".story-phone img")];
    const dots = [...story.querySelectorAll(".story-dots i")];
    const show = i => {
      steps.forEach((s, k) => s.classList.toggle("on", k === i));
      shots.forEach((s, k) => s.classList.toggle("on", k === i));
      dots.forEach((s, k) => s.classList.toggle("on", k === i));
    };
    const sio = new IntersectionObserver(es => es.forEach(e => {
      if (e.isIntersecting) show(steps.indexOf(e.target));
    }), { rootMargin: "-45% 0px -45% 0px" });
    steps.forEach(s => sio.observe(s));
    show(0);
  });
})();
