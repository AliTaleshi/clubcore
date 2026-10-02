import { Link as RouterLink } from 'react-router-dom';
import { Button, Stack, Typography } from '@mui/material';

export default function NotFoundPage() {
  return (
    <Stack alignItems="center" spacing={2} sx={{ py: 10 }}>
      <Typography variant="h3">۴۰۴</Typography>
      <Typography color="text.secondary">صفحه مورد نظر یافت نشد</Typography>
      <Button variant="contained" component={RouterLink} to="/">بازگشت</Button>
    </Stack>
  );
}
