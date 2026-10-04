import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Stack, Typography } from '@mui/material';
import ErrorOutline from '@mui/icons-material/ErrorOutline';

const RELOAD_FLAG = 'cc.chunk-reload';

/** After a deploy, an open tab may request code chunks that no longer exist. */
function isChunkLoadError(error: Error): boolean {
  return /dynamically imported module|Importing a module script failed|ChunkLoadError|Loading chunk/i.test(
    `${error.name} ${error.message}`,
  );
}

interface Props {
  children: ReactNode;
  /** Changing this (e.g. the current path) clears the error so navigation recovers. */
  resetKey?: string;
}

/** Shows a Persian error page instead of a blank screen when rendering fails. */
export class ErrorBoundary extends Component<Props, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Render error', error, info.componentStack);
    if (isChunkLoadError(error)) {
      // Reload to fetch the new build, at most once per 30 s so a server that is really down can't cause a loop.
      try {
        const last = Number(sessionStorage.getItem(RELOAD_FLAG) ?? 0);
        if (Date.now() - last > 30_000) {
          sessionStorage.setItem(RELOAD_FLAG, String(Date.now()));
          window.location.reload();
        }
      } catch {
        /* storage unavailable */
      }
    }
  }

  componentDidUpdate(prev: Props) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;
    const chunk = isChunkLoadError(this.state.error);
    return (
      <Stack alignItems="center" spacing={2} sx={{ py: 10, px: 2, textAlign: 'center' }} role="alert">
        <ErrorOutline color="error" sx={{ fontSize: 56 }} />
        <Typography variant="h6">
          {chunk ? 'نسخه جدید سامانه منتشر شده است' : 'مشکلی در نمایش این صفحه پیش آمد'}
        </Typography>
        <Typography color="text.secondary">
          {chunk ? 'برای ادامه صفحه را دوباره بارگذاری کنید.' : 'لطفاً صفحه را دوباره بارگذاری کنید. اگر مشکل ادامه داشت با پشتیبانی تماس بگیرید.'}
        </Typography>
        <Button variant="contained" onClick={() => window.location.reload()}>بارگذاری مجدد</Button>
      </Stack>
    );
  }
}
