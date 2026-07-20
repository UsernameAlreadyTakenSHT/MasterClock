# MasterClock v0.8.0

A professional-grade, multi-layered timing station for Chess, Wargames, and Tabletop competitions. **MasterClock** modernizes the traditional chess clock with a robust architectural decoupling between classic gameplay and complex multi-phase structures.

---

## 🏗️ Architectural Core
- **Classic Engine**: High-performance, low-latency motor for traditional time controls (Fischer, Bronstein, Byoyomi).
- **Omni Engine**: A dedicated 6-layer state machine managing Global > Game > Round > Turn > Phase sequences.
- **Isolation**: Complete decoupling via standalone ViewModels to ensure stability and battery efficiency.

---

## 🕒 Exhaustive Timing Modes
- **Classic**: Sudden Death, Fisher (Increment), Bronstein (Delay), US Delay.
- **Advanced Omni**: 
  - Sequential Game Lists with unique durations.
  - Custom Round behaviors (Loop/Advance).
  - Multi-phase turn structures (Think > Move > Resolve).
- **Traditional**: Japanese, Canadian, and Progressive Byoyomi.
- **Specialty**: Hourglass, Gong (Simultaneous/Turn-based), Hidden/Random time, and Move Counters.
- **Economy**: Time Banking (Accumulative/Reserve) with customizable reset scopes.

---

## 🛠️ Technical Polish
- **Flavor Segmentation**: Optimized builds (Complete, Standard, Light, ExtraLight).
- **Data Portability**: Full .zip backups including multimedia notes (Voice/Images).
- **Connectivity**: QR Code structure sharing and Bluetooth Board support.
- **UI/UX**: Minimalist "Noir et Blanc" aesthetic with instantaneous native launch.

---

## 📜 Credits & Licensing
- **Logo Icon**: The clock icon used in the logo is from [Paweł Kuna](https://opensvg.dev/icons) (v3.44.0). Licensed under **MIT**.
- **Chess Pieces**: "Cburnett" style icons sourced from [Wikimedia Commons](https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces). Created by [Cburnett](https://en.wikipedia.org/wiki/User:Cburnett/GFDL_images/Chess). Licensed under **GFDL** and **CC BY-SA 3.0**.
- **Audio Assets**:
  - **Gong**: [Zen Gong - Alex_Jauk](https://pixabay.com/sound-effects/film-special-effects-zen-gong-199844/)
  - **Beep**: [Beep - u_edtmwfwu7c](https://pixabay.com/sound-effects/film-special-effects-beep-329314/)
  - **Final Beep**: [Public Domain Beep Sound - qubodup](https://pixabay.com/sound-effects/public-domain-beep-sound-100267/)
  - **Switch**: [Light Switch - Pixabay](https://pixabay.com/sound-effects/film-special-effects-light-switch-82388/)

*Project licensed under the MIT License.*
