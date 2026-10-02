import { Card, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Attendance, Page } from '../../api/types';
import { Empty, Loading, PageHeader } from '../../components/common';
import { faDigits, formatDate, formatTime } from '../../utils/format';
import { entryMethod } from '../../utils/labels';

export default function MyAttendancePage() {
  const { data, isLoading } = useQuery({
    queryKey: ['my-attendance'],
    queryFn: () => api.get<Page<Attendance>>('/me/attendance', { params: { size: 100 } }).then((r) => r.data),
  });
  return (
    <>
      <PageHeader title="سوابق حضور" subtitle={data ? `${faDigits(data.totalElements)} جلسه در ۳۰ روز اخیر` : undefined} />
      <Card>
        {isLoading ? <Loading /> : !data?.content.length ? <Empty /> : (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow><TableCell>تاریخ</TableCell><TableCell>ورود</TableCell><TableCell>خروج</TableCell><TableCell>روش</TableCell></TableRow></TableHead>
              <TableBody>
                {data.content.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell>{formatDate(a.checkInAt)}</TableCell>
                    <TableCell>{formatTime(a.checkInAt)}</TableCell>
                    <TableCell>{a.checkOutAt ? formatTime(a.checkOutAt) : 'در باشگاه'}</TableCell>
                    <TableCell>{entryMethod[a.method]}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </>
  );
}
