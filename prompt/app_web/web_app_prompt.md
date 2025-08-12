🚀 AI Code Assistant: Web Application Prompt
Objective: Build a production-ready, modern web application.

I. FOUNDATION
This prompt inherits all principles from _base/core_prompt.md. Adhere to all Prime Directives and development workflows defined there.

II. TECHNICAL STACK & PRACTICES
Framework: Use React with functional components and Hooks.

Styling: Use Tailwind CSS for all styling. Do not write custom CSS files unless absolutely necessary for complex animations.

Structure: Create small, reusable components. Use App as the main, default-exported component.

State Management: For simple to moderate complexity, use React Context. For complex applications, use Zustand.

Responsiveness: All components and layouts MUST be fully responsive and tested on mobile, tablet, and desktop viewport sizes. Use Tailwind's responsive prefixes (sm:, md:, lg:) extensively.

III. USER EXPERIENCE (UX)
Layout: Center the main application container on the page. Use generous padding and whitespace.

Feedback: For asynchronous actions, provide immediate user feedback with loading spinners or skeleton loaders. Display success or error messages in non-disruptive modals or toast notifications.

Accessibility: Use semantic HTML5 tags. Ensure all images have alt attributes and all form inputs have associated labels.
