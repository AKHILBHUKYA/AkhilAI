import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{js,ts,jsx,tsx}", "./components/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#eef7ff",
          100: "#d9ecff",
          200: "#bcdfff",
          300: "#8ecaff",
          400: "#59aaff",
          500: "#3388ff",
          600: "#1a66f5",
          700: "#1450e1",
          800: "#1742b6",
          900: "#193a8f",
        },
      },
    },
  },
  plugins: [],
};
export default config;
