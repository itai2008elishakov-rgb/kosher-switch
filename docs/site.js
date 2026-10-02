// Motion for every page of the Kosher Switch website.
// One scroll listener drives everything through requestAnimationFrame, and only
// transform/opacity change, so it stays smooth. Reduced motion shows final states.
(() => {
  const root = document.documentElement;
  const still = matchMedia("(prefers-reduced-motion: reduce)").matches || root.classList.contains("still");
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));

  // Global nav: hairline once scrolled, and the phone menu.
  const nav = document.querySelector(".gnav");
  const menuBtn = document.querySelector(".menu-btn");
  const setMenu = open => {
    root.classList.toggle("menu-open", open);
    if (menuBtn) menuBtn.setAttribute("aria-expanded", open);
    document.body.style.overflow = open ? "hidden" : "";
  };
  if (menuBtn) {
    menuBtn.addEventListener("click", () => setMenu(!root.classList.contains("menu-open")));
    document.querySelectorAll(".sheet a").forEach(a => a.addEventListener("click", () => setMenu(false)));
    addEventListener("keydown", e => { if (e.key === "Escape") setMenu(false); });
  }

  // Scroll progress line.
  const bar = document.createElement("div");
  bar.className = "progress";
  document.body.appendChild(bar);

  // Reveal on scroll; children of [data-stagger] come in one after another.
  document.querySelectorAll("[data-stagger]").forEach(g => [...g.children].forEach((c, i) => {
    c.classList.add("rv"); c.style.setProperty("--i", i % 6);
  }));
  if (still) document.querySelectorAll(".rv").forEach(el => el.classList.add("in"));
  else {
    const io = new IntersectionObserver(es => es.forEach(e => {
      if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
    }), { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
    document.querySelectorAll(".rv").forEach(el => io.observe(el));
    // Whatever is already on screen comes in right after the first paint.
    setTimeout(() => document.querySelectorAll(".rv").forEach(el => {
      if (el.getBoundingClientRect().top < innerHeight * 0.95) { el.classList.add("in"); io.unobserve(el); }
    }), 60);
  }

  // Words that light up as you read: split the text into words once.
  const wordBlocks = [...document.querySelectorAll("[data-words]")].map(el => {
    const gold = new Set((el.dataset.gold || "").split("|").filter(Boolean));
    const words = el.textContent.trim().split(/\s+/);
    el.textContent = "";
    const spans = words.map((w, i) => {
      const s = document.createElement("span");
      s.className = "w" + ([...gold].some(g => w.replace(/[.,:]/g, "") === g) ? " g" : "");
      s.textContent = w;
      el.appendChild(s);
      if (i < words.length - 1) el.appendChild(document.createTextNode(" "));
      return s;
    });
    if (still) spans.forEach(s => s.classList.add("lit"));
    return { el, spans };
  });

  // Pinned scenes: --p goes 0 → 1 while the scene scrolls past; past halfway it turns kosher.
  const scenes = [...document.querySelectorAll(".scene")];
  if (still) scenes.forEach(s => { s.style.setProperty("--p", 1); s.classList.add("is-on"); s.querySelectorAll(".dev").forEach(d => d.classList.add("on")); });

  const frame = () => {
    const vh = innerHeight;
    const max = root.scrollHeight - vh;
    bar.style.transform = `scaleX(${max > 0 ? scrollY / max : 0})`;
    if (nav) nav.classList.toggle("scrolled", scrollY > 8);
    if (still) return;
    for (const s of scenes) {
      const r = s.getBoundingClientRect();
      if (r.bottom < -vh || r.top > vh * 2) continue;
      const p = clamp(-r.top / (r.height - vh));
      s.style.setProperty("--p", p.toFixed(4));
      if (s.dataset.manual) continue;
      const on = p > 0.42;
      if (on !== s.classList.contains("is-on")) {
        s.classList.toggle("is-on", on);
        s.querySelectorAll(".dev").forEach(d => d.classList.toggle("on", on));
        const sw = s.querySelector(".switch-ui"); if (sw) sw.setAttribute("aria-pressed", on);
      }
    }
    for (const { el, spans } of wordBlocks) {
      const r = el.getBoundingClientRect();
      if (r.bottom < 0 || r.top > vh) continue;
      const p = clamp((vh * 0.85 - r.top) / (r.height + vh * 0.4));
      const n = Math.round(p * spans.length);
      spans.forEach((w, i) => w.classList.toggle("lit", i < n));
    }
  };
  let ticking = false;
  const onScroll = () => { if (!ticking) { ticking = true; requestAnimationFrame(() => { frame(); ticking = false; }); } };
  addEventListener("scroll", onScroll, { passive: true });
  addEventListener("resize", onScroll);
  frame();

  // Tappable open/kosher switches. Inside a scene, a tap takes over from scrolling.
  document.querySelectorAll(".switch-ui").forEach(t => {
    const host = t.closest(".scene, .switch-host");
    t.addEventListener("click", () => {
      const on = !host.classList.contains("is-on");
      if (host.classList.contains("scene")) host.dataset.manual = "1";
      host.classList.toggle("is-on", on);
      host.querySelectorAll(".dev").forEach(d => d.classList.toggle("on", on));
      t.setAttribute("aria-pressed", on);
      if (navigator.vibrate) navigator.vibrate(10);
    });
  });

  // Sticky stories: the device shows the screen of the step in the middle of the view.
  document.querySelectorAll(".story").forEach(story => {
    const steps = [...story.querySelectorAll(".story-step")];
    const shots = [...story.querySelectorAll(".story-stick .scr img")];
    const dots = [...story.querySelectorAll(".dots i")];
    const show = i => [steps, shots, dots].forEach(list => list.forEach((el, k) => el.classList.toggle("on", k === i)));
    const sio = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) show(steps.indexOf(e.target)); }),
      { rootMargin: "-45% 0px -45% 0px" });
    steps.forEach(s => sio.observe(s));
    show(0);
  });

  // Galleries: arrow buttons scroll one screen at a time.
  document.querySelectorAll("[data-gallery]").forEach(nav => {
    const g = document.getElementById(nav.dataset.gallery);
    const [prev, next] = nav.querySelectorAll("button");
    const update = () => { prev.disabled = g.scrollLeft < 8; next.disabled = g.scrollLeft > g.scrollWidth - g.clientWidth - 8; };
    const step = d => g.scrollBy({ left: d * Math.max(260, g.clientWidth * 0.7), behavior: still ? "auto" : "smooth" });
    prev.addEventListener("click", () => step(-1));
    next.addEventListener("click", () => step(1));
    g.addEventListener("scroll", update, { passive: true });
    update();
  });

  // Count-up numbers.
  const counters = document.querySelectorAll("[data-count]");
  if (!still) {
    const cio = new IntersectionObserver(es => es.forEach(e => {
      if (!e.isIntersecting) return;
      const el = e.target, to = parseFloat(el.dataset.count), t0 = performance.now(), dur = 1400;
      const tick = t => {
        const k = clamp((t - t0) / dur), ease = 1 - Math.pow(1 - k, 3);
        el.textContent = Math.round(to * ease) + (el.dataset.suffix || "");
        if (k < 1) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
      cio.unobserve(el);
    }), { threshold: 0.6 });
    counters.forEach(el => { el.textContent = "0" + (el.dataset.suffix || ""); cio.observe(el); });
  }

  // FAQ: answers open and close smoothly.
  document.querySelectorAll(".faq details").forEach(d => {
    const sum = d.querySelector("summary"), ans = d.querySelector(".ans");
    if (!ans || still) return;
    sum.addEventListener("click", e => {
      e.preventDefault();
      if (d.open) {
        const h = ans.scrollHeight;
        ans.animate([{ height: h + "px", opacity: 1 }, { height: "0px", opacity: 0 }], { duration: 380, easing: "cubic-bezier(.22,.9,.24,1)" })
          .onfinish = () => { d.open = false; };
      } else {
        d.open = true;
        const h = ans.scrollHeight;
        ans.animate([{ height: "0px", opacity: 0 }, { height: h + "px", opacity: 1 }], { duration: 480, easing: "cubic-bezier(.22,.9,.24,1)" });
      }
    });
  });

  // Gentle tilt toward the pointer on devices marked data-tilt (mouse only).
  if (!still && matchMedia("(pointer: fine)").matches) document.querySelectorAll("[data-tilt]").forEach(el => {
    const max = parseFloat(el.dataset.tilt) || 8;
    el.addEventListener("pointermove", e => {
      const r = el.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
      el.style.transform = `perspective(1000px) rotateY(${x * max}deg) rotateX(${-y * max}deg)`;
    });
    el.addEventListener("pointerleave", () => { el.style.transform = ""; });
  });
})();
